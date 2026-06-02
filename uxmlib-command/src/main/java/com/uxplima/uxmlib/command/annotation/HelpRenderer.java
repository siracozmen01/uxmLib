package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/**
 * Renders a command's help: one clickable line per branch the sender may use, paginated. Each line suggests
 * its command on click (so the player can fill in the arguments) and shows its description on hover; a footer
 * offers clickable previous/next page buttons that re-run {@code /root help <page>}. Branches whose permission
 * the sender lacks are filtered out before paging, so help never advertises — or pages past — a command they
 * cannot run. The page-arithmetic is delegated to {@link HelpPages}; this only lays the slice out as
 * components.
 */
final class HelpRenderer {

    private HelpRenderer() {}

    /** How many help lines a page shows before the previous/next footer. */
    static final int PER_PAGE = 7;

    /** A single help line: how the branch is invoked, its description, and the permission gating it. */
    record Entry(String usage, String description, String permission) {}

    /** A {@code help [page]} node that lists the visible branches of {@code root}, paginated, when run. */
    static LiteralArgumentBuilder<CommandSourceStack> helpLiteral(String root, List<Method> branches) {
        List<Entry> entries = entriesOf(branches);
        return Cmd.literal("help")
                .executes(ctx -> show(ctx, root, entries, 1))
                .then(Cmd.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> show(ctx, root, entries, IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private static List<Entry> entriesOf(List<Method> branches) {
        List<Entry> entries = new ArrayList<>();
        for (Method method : branches) {
            Subcommand sub = method.getAnnotation(Subcommand.class);
            Permission permission = method.getAnnotation(Permission.class);
            entries.add(
                    new Entry(usageOf(method, sub), sub.description(), permission == null ? "" : permission.value()));
        }
        return entries;
    }

    /** The usage path of a branch: its literal path plus {@code <name>}/{@code [name]} per argument. */
    private static String usageOf(Method method, Subcommand sub) {
        StringBuilder usage = new StringBuilder(sub.value());
        for (Parameter param : method.getParameters()) {
            Arg arg = param.getAnnotation(Arg.class);
            if (arg != null) {
                if (usage.length() > 0) {
                    usage.append(' ');
                }
                usage.append(arg.optional() ? "[" + arg.value() + "]" : "<" + arg.value() + ">");
            }
        }
        return usage.toString();
    }

    /** Filter the entries the sender may use, render the requested page, and send it. */
    private static int show(CommandContext<CommandSourceStack> ctx, String root, List<Entry> entries, int page) {
        CommandSourceStack source = ctx.getSource();
        List<Entry> visible = visibleTo(source, entries);
        Sender.of(source).send(render(root, visible, page, PER_PAGE));
        return Cmd.OK;
    }

    private static List<Entry> visibleTo(CommandSourceStack source, List<Entry> entries) {
        List<Entry> visible = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.permission().isEmpty() || source.getSender().hasPermission(entry.permission())) {
                visible.add(entry);
            }
        }
        return visible;
    }

    /**
     * The full help page as one component: a header, every clickable line on the (clamped) {@code page}, and a
     * previous/next footer when there is more than one page. Pure — no Brigadier, no live sender — so the
     * layout (clickable lines, page footer) is unit-tested directly.
     */
    static Component render(String root, List<Entry> entries, int page, int perPage) {
        int clamped = HelpPages.clamp(page, entries.size(), perPage);
        int pages = HelpPages.pageCount(entries.size(), perPage);
        Component message = Component.text("/" + root + " help (" + clamped + "/" + pages + ")", NamedTextColor.YELLOW);
        for (Entry entry : HelpPages.slice(entries, clamped, perPage)) {
            message = message.append(line(root, entry));
        }
        Component footer = footer(root, clamped, pages);
        return footer == null ? message : message.append(footer);
    }

    /** One clickable help line: suggests its command on click and shows the description on hover. */
    private static Component line(String root, Entry entry) {
        String command = "/" + root + (entry.usage().isEmpty() ? "" : " " + entry.usage());
        Component usage = Component.text("\n" + command, NamedTextColor.WHITE)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(hoverText(entry)));
        if (entry.description().isEmpty()) {
            return usage;
        }
        return usage.append(Component.text(" — " + entry.description(), NamedTextColor.GRAY));
    }

    private static Component hoverText(Entry entry) {
        String hover = entry.description().isEmpty() ? "Click to fill in this command" : entry.description();
        return Component.text(hover);
    }

    /** A previous/next footer that re-runs {@code /root help <page>}; {@code null} for a single-page list. */
    private static @org.jspecify.annotations.Nullable Component footer(String root, int page, int pages) {
        if (pages <= 1) {
            return null;
        }
        Component footer = Component.text("\n", NamedTextColor.DARK_GRAY);
        if (page > 1) {
            footer = footer.append(pageButton(root, "« prev", page - 1, NamedTextColor.AQUA));
        }
        if (page > 1 && page < pages) {
            footer = footer.append(Component.text("  ", NamedTextColor.DARK_GRAY));
        }
        if (page < pages) {
            footer = footer.append(pageButton(root, "next »", page + 1, NamedTextColor.AQUA));
        }
        return footer;
    }

    private static Component pageButton(String root, String label, int target, NamedTextColor color) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.runCommand("/" + root + " help " + target))
                .hoverEvent(HoverEvent.showText(Component.text("Page " + target)));
    }
}

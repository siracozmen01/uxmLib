package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/**
 * Renders a command's help: one line per branch the sender is allowed to use, as an Adventure component.
 * Each entry carries the branch's usage path and its description; branches whose permission the sender
 * lacks are skipped so help never advertises a command they cannot run.
 */
final class HelpRenderer {

    private HelpRenderer() {}

    /** A single help line: how the branch is invoked, its description, and the permission gating it. */
    record Entry(String usage, String description, String permission) {}

    /** A {@code help} literal node that lists the visible branches of {@code root} when run. */
    static LiteralArgumentBuilder<CommandSourceStack> helpLiteral(String root, List<Method> branches) {
        List<Entry> entries = new ArrayList<>();
        for (Method method : branches) {
            Subcommand sub = method.getAnnotation(Subcommand.class);
            Permission permission = method.getAnnotation(Permission.class);
            entries.add(
                    new Entry(usageOf(method, sub), sub.description(), permission == null ? "" : permission.value()));
        }
        return Cmd.literal("help").executes(ctx -> {
            send(ctx, root, entries);
            return Cmd.OK;
        });
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

    /** Send {@code entries} the sender may use to them, headed by the root command name. */
    static void send(CommandContext<CommandSourceStack> ctx, String root, List<Entry> entries) {
        CommandSourceStack source = ctx.getSource();
        Component message = Component.text("/" + root + " help", NamedTextColor.YELLOW);
        for (Entry entry : entries) {
            if (!entry.permission().isEmpty() && !source.getSender().hasPermission(entry.permission())) {
                continue;
            }
            message = message.append(line(root, entry));
        }
        Sender.of(source).send(message);
    }

    private static Component line(String root, Entry entry) {
        Component usage = Component.text(
                "\n/" + root + (entry.usage().isEmpty() ? "" : " " + entry.usage()), NamedTextColor.WHITE);
        if (entry.description().isEmpty()) {
            return usage;
        }
        return usage.append(Component.text(" — " + entry.description(), NamedTextColor.GRAY));
    }
}

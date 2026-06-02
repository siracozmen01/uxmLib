package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers the clickable, paginated {@code /help}: the generated help node accepts a {@code [page]} argument,
 * and the rendered page carries clickable lines (a suggest-command per branch) plus a previous/next footer
 * when the branch list spans more than one page. The rendering is checked through the pure
 * {@link HelpRenderer#render} so it needs no live sender; the node shape is checked off the built tree.
 */
class HelpPaginationTest {

    @Command(name = "town")
    static class TownCommand {
        @Subcommand(value = "create", description = "Found a town")
        void create(Sender sender) {}

        @Subcommand(value = "delete", description = "Disband your town")
        void delete(Sender sender) {}
    }

    private static List<HelpRenderer.Entry> many(int count) {
        List<HelpRenderer.Entry> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new HelpRenderer.Entry("sub" + i, "branch " + i, ""));
        }
        return list;
    }

    @Test
    void helpNodeTakesAPageArgument() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new TownCommand());
        CommandNode<CommandSourceStack> help = node.getChild("help");
        assertThat(help).isNotNull();
        assertThat(java.util.Objects.requireNonNull(help).getCommand()).isNotNull(); // /town help runs page 1
        CommandNode<CommandSourceStack> page =
                java.util.Objects.requireNonNull(help).getChild("page");
        assertThat(page).isNotNull();
        assertThat(java.util.Objects.requireNonNull(page).getCommand()).isNotNull();
    }

    @Test
    void eachLineSuggestsItsCommandOnClick() {
        List<HelpRenderer.Entry> entries = List.of(new HelpRenderer.Entry("create", "Found a town", ""));
        Component page = HelpRenderer.render("town", entries, 1, HelpRenderer.PER_PAGE);
        assertThat(clickValues(page)).contains("/town create");
    }

    @Test
    void aMultiPageListShowsANextButton() {
        Component page1 = HelpRenderer.render("town", many(20), 1, HelpRenderer.PER_PAGE);
        String text = PlainTextComponentSerializer.plainText().serialize(page1);
        assertThat(text).contains("(1/3)").contains("next");
        // The next button runs the help command for page 2.
        assertThat(clickValues(page1)).contains("/town help 2");
    }

    @Test
    void thePageHeaderCountsTheVisiblePages() {
        Component lastPage = HelpRenderer.render("town", many(20), 3, HelpRenderer.PER_PAGE);
        String text = PlainTextComponentSerializer.plainText().serialize(lastPage);
        assertThat(text).contains("(3/3)").contains("prev");
    }

    @Test
    void aSinglePageHasNoFooter() {
        Component page = HelpRenderer.render("town", many(2), 1, HelpRenderer.PER_PAGE);
        assertThat(clickValues(page)).noneMatch(v -> v.contains("help "));
    }

    /** The values of every click event in the component tree, in pre-order. */
    private static List<String> clickValues(Component component) {
        List<String> values = new java.util.ArrayList<>();
        collectClicks(component, values);
        return values;
    }

    private static void collectClicks(Component component, List<String> into) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) {
            into.add(text.value());
        }
        for (Component child : component.children()) {
            collectClicks(child, into);
        }
    }
}

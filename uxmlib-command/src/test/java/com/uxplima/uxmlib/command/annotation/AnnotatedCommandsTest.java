package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import org.junit.jupiter.api.Test;

/**
 * Verifies the reflective registrar builds the expected Brigadier tree shape and rejects malformed
 * handlers at registration. {@link AnnotatedCommands#buildNode} constructs the tree without a live
 * server, so the structure can be asserted directly.
 */
class AnnotatedCommandsTest {

    @Command(name = "shop", aliases = "store", description = "Open the shop")
    static class ShopCommand {
        @Subcommand("")
        void root(Sender sender) {}

        @Subcommand("buy")
        void buy(Sender sender, @Arg("item") String item, @Arg(value = "amount", min = 1, max = 64) int amount) {}

        @Subcommand("admin reload")
        @Permission("shop.admin")
        void reload(CommandSourceStack source) {}
    }

    @Command(name = "broken")
    static class UnsupportedArg {
        @Subcommand("go")
        void go(@Arg("where") java.util.List<String> where) {}
    }

    @Command(name = "empty")
    static class NoSubcommands {}

    static class NoAnnotation {}

    @Command(name = "unannotated")
    static class UnannotatedParam {
        @Subcommand("go")
        void go(Sender sender, String forgotTheArgAnnotation) {}
    }

    @Test
    void buildsRootAndNestedLiteralBranches() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new ShopCommand());

        assertThat(node.getLiteral()).isEqualTo("shop");

        CommandNode<CommandSourceStack> buy = node.getChild("buy");
        assertThat(buy).isNotNull();
        CommandNode<CommandSourceStack> item = buy.getChild("item");
        assertThat(item).isNotNull();
        assertThat(item.getChild("amount")).isNotNull();

        CommandNode<CommandSourceStack> admin = node.getChild("admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getChild("reload")).isNotNull();
    }

    @Test
    void rejectsAClassWithoutCommandAnnotation() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new NoAnnotation()))
                .isInstanceOf(CommandParseException.class);
    }

    @Test
    void rejectsAHandlerWithNoSubcommands() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new NoSubcommands()))
                .isInstanceOf(CommandParseException.class);
    }

    @Test
    void rejectsAnUnsupportedArgumentType() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new UnsupportedArg()))
                .isInstanceOf(CommandParseException.class);
    }

    @Test
    void rejectsAParameterThatIsNeitherInjectableNorAnnotated() {
        // A plain String parameter with no @Arg would crash at command-run time; catch it at registration.
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new UnannotatedParam()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("@Arg");
    }
}

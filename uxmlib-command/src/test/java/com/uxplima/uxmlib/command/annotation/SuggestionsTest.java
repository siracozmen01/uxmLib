package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/** Verifies tab-completion is wired from @Suggest, @SuggestWith, and enum constants. */
class SuggestionsTest {

    enum Mode {
        SURVIVAL,
        CREATIVE
    }

    public static final class FruitSuggestions implements SuggestionSource {
        @Override
        public java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
            return builder.suggest("apple").suggest("banana").buildFuture();
        }
    }

    @Command(name = "sugg")
    static class SuggestCommand {
        @Subcommand("toggle")
        void toggle(Sender sender, @Arg("state") @Suggest({"on", "off"}) String state) {}

        @Subcommand("mode")
        void mode(Sender sender, @Arg("mode") Mode mode) {}

        @Subcommand("fruit")
        void fruit(Sender sender, @Arg("fruit") @SuggestWith(FruitSuggestions.class) String fruit) {}
    }

    @SuppressWarnings("unchecked")
    private static @org.jspecify.annotations.Nullable ArgumentCommandNode<CommandSourceStack, ?> arg(
            LiteralCommandNode<CommandSourceStack> root, String literal, String argName) {
        CommandNode<CommandSourceStack> lit = root.getChild(literal);
        if (lit == null) {
            return null;
        }
        CommandNode<CommandSourceStack> a = lit.getChild(argName);
        return a instanceof ArgumentCommandNode ? (ArgumentCommandNode<CommandSourceStack, ?>) a : null;
    }

    @Test
    void staticSuggestListIsWired() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new SuggestCommand());
        ArgumentCommandNode<CommandSourceStack, ?> state = arg(node, "toggle", "state");
        assertThat(state).isNotNull();
        assertThat(java.util.Objects.requireNonNull(state).getCustomSuggestions())
                .isNotNull();
    }

    @Test
    void enumConstantsDriveSuggestions() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new SuggestCommand());
        ArgumentCommandNode<CommandSourceStack, ?> mode = arg(node, "mode", "mode");
        assertThat(mode).isNotNull();
        assertThat(java.util.Objects.requireNonNull(mode).getCustomSuggestions())
                .isNotNull();
    }

    @Test
    void suggestWithProviderIsWired() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new SuggestCommand());
        ArgumentCommandNode<CommandSourceStack, ?> fruit = arg(node, "fruit", "fruit");
        assertThat(fruit).isNotNull();
        assertThat(java.util.Objects.requireNonNull(fruit).getCustomSuggestions())
                .isNotNull();
    }
}

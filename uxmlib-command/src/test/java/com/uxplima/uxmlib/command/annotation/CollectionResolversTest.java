package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Covers the composing collection resolvers: a {@code List<T>} greedily maps every remaining token through
 * the element resolver, and an {@code Optional<T>} is present only when a token was given. The mapping logic
 * is exercised directly against a mocked source so it needs no live server; the node shape is checked through
 * the built tree.
 */
class CollectionResolversTest {

    /** A throwaway element resolver over an int word, so the list/optional logic can be checked source-free. */
    private static ParamResolver<Integer> intElement() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return IntegerArgumentType.integer();
            }

            @Override
            public Integer resolve(CommandContext<CommandSourceStack> context, String name) {
                return IntegerArgumentType.getInteger(context, name);
            }
        };
    }

    private static ParamResolver<String> wordElement() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return StringArgumentType.word();
            }

            @Override
            public String resolve(CommandContext<CommandSourceStack> context, String name) {
                return StringArgumentType.getString(context, name);
            }
        };
    }

    @Test
    void listGreedilyMapsEveryToken() {
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        List<Object> values = CollectionResolvers.resolveTokens(intElement(), source, "1 2 3");
        assertThat(values).containsExactly(1, 2, 3);
    }

    @Test
    void listOfOneTokenHasOneElement() {
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        List<Object> values = CollectionResolvers.resolveTokens(wordElement(), source, "alpha");
        assertThat(values).containsExactly("alpha");
    }

    @Test
    void blankRawYieldsAnEmptyList() {
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        assertThat(CollectionResolvers.resolveTokens(wordElement(), source, "   "))
                .isEmpty();
    }

    @Test
    void optionalIsPresentWhenATokenIsGiven() {
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        Optional<Object> present = CollectionResolvers.resolveOptional(intElement(), source, "7");
        assertThat(present).contains(7);
    }

    @Test
    void optionalIsAbsentWhenNoTokenIsGiven() {
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        assertThat(CollectionResolvers.resolveOptional(intElement(), source, ""))
                .isEmpty();
    }

    @Command(name = "coll")
    static class CollectionCommand {
        @Subcommand("give")
        void give(Sender sender, @Arg("ids") List<Integer> ids) {}

        @Subcommand("note")
        void note(Sender sender, @Arg(value = "reason", optional = true) Optional<String> reason) {}
    }

    @Test
    void listAndOptionalParamsBuildArgumentNodes() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new CollectionCommand());
        CommandNode<CommandSourceStack> give = node.getChild("give");
        assertThat(give).isNotNull();
        assertThat(java.util.Objects.requireNonNull(give).getChild("ids")).isNotNull();
        CommandNode<CommandSourceStack> note = node.getChild("note");
        assertThat(note).isNotNull();
        assertThat(java.util.Objects.requireNonNull(note).getChild("reason")).isNotNull();
        // An optional Optional<T> arg means the literal itself is executable (running with no token is valid).
        assertThat(java.util.Objects.requireNonNull(note).getCommand()).isNotNull();
    }
}

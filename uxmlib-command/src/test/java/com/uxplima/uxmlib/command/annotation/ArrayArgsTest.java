package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers trailing array ("orphan") arguments: a {@code T[]} parameter greedily consumes every remaining token
 * and maps each through the element type's resolver, the same way a {@code List<T>} does. The greedy node is
 * built and the registration-time ordering rules (only a trailing parameter may consume the rest) still apply.
 */
class ArrayArgsTest {

    @Command(name = "msg")
    static class ArrayCommand {
        @Subcommand("send")
        void send(Sender sender, @Arg("words") String[] words) {}
    }

    @Command(name = "nums")
    static class IntArrayCommand {
        @Subcommand("sum")
        void sum(Sender sender, @Arg("values") int[] values) {}
    }

    @Command(name = "badarray")
    static class ArrayNotLast {
        @Subcommand("go")
        void go(Sender sender, @Arg("words") String[] words, @Arg("n") int n) {}
    }

    @Test
    void arrayArgBuildsAGreedyTrailingNode() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new ArrayCommand());
        CommandNode<CommandSourceStack> send = node.getChild("send");
        assertThat(send).isNotNull();
        assertThat(java.util.Objects.requireNonNull(send).getChild("words")).isNotNull();
    }

    @Test
    void primitiveArrayArgBuildsAGreedyTrailingNode() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new IntArrayCommand());
        CommandNode<CommandSourceStack> sum = node.getChild("sum");
        assertThat(sum).isNotNull();
        assertThat(java.util.Objects.requireNonNull(sum).getChild("values")).isNotNull();
    }

    @Test
    void rejectsAnArrayThatIsNotTheLastArgument() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new ArrayNotLast()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("greedy");
    }
}

package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.CommandPriority;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code @}{@link CommandPriority} orders ambiguous sibling branches so the higher-priority one is
 * attached first. Brigadier tries argument siblings in insertion order and runs the first that parses, so the
 * branch attached first wins when two overlapping overloads could both consume the same input. The tree shape
 * is asserted directly through {@link AnnotatedCommands#buildNode} without a live server.
 */
class CommandPriorityTest {

    @Command(name = "give", help = false)
    static class PrioritisedCommand {
        // Both branches attach a single argument directly under the root; without priorities their sibling
        // order is reflection-defined. The lower priority value should attach first.
        @Subcommand("")
        @CommandPriority(5)
        void byName(Sender sender, @Arg("name") String name) {}

        @Subcommand("")
        @CommandPriority(1)
        void byAmount(Sender sender, @Arg("amount") int amount) {}
    }

    @Command(name = "warp", help = false)
    static class MixedPriorityCommand {
        @Subcommand("")
        void noPriority(Sender sender, @Arg("text") String text) {}

        @Subcommand("")
        @CommandPriority(1)
        void prioritised(Sender sender, @Arg("number") int number) {}
    }

    @Test
    void lowerPriorityValueAttachesFirst() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new PrioritisedCommand());
        List<String> argOrder =
                node.getChildren().stream().map(CommandNode::getName).toList();
        // priority(1) byAmount before priority(5) byName: the lower value is the higher priority.
        assertThat(argOrder).containsExactly("amount", "name");
    }

    @Test
    void aPrioritisedBranchOutranksAnUnprioritisedSibling() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new MixedPriorityCommand());
        List<String> argOrder =
                node.getChildren().stream().map(CommandNode::getName).toList();
        // The explicitly prioritised branch sorts ahead of the one that declares no priority.
        assertThat(argOrder).containsExactly("number", "text");
    }
}

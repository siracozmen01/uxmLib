package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * A {@link CompletableFuture}-returning {@code @Subcommand} registers exactly like a void one: the node
 * tree is identical (the async-ness lives only in the executor body), and sync (void) branches keep
 * building unchanged. MockBukkit cannot dispatch the tree, so the wiring is smoke-tested at the node level
 * with the synchronous {@code buildNode} overload that takes a scheduler double.
 */
class AsyncCommandNodeTest {

    @Command(name = "bal")
    static class BalanceCommand {
        // Async branch: the framework only routes its completion; the future is the handler's own.
        @Subcommand("check")
        CompletableFuture<Void> check(Sender sender, @Arg("name") String name) {
            return CompletableFuture.completedFuture(null);
        }

        // A sync branch alongside it still builds as before.
        @Subcommand("ping")
        void ping(Sender sender) {}
    }

    @Test
    void anAsyncSubcommandBuildsTheSameNodeTreeAsAVoidOne() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(
                new BalanceCommand(), ParamResolvers.withDefaults(), new SameThreadScheduler());

        assertThat(node.getLiteral()).isEqualTo("bal");

        CommandNode<CommandSourceStack> check = node.getChild("check");
        assertThat(check).isNotNull();
        CommandNode<CommandSourceStack> name = check.getChild("name");
        assertThat(name).isNotNull();
        assertThat(name.getCommand()).isNotNull();

        CommandNode<CommandSourceStack> ping = node.getChild("ping");
        assertThat(ping).isNotNull();
        assertThat(ping.getCommand()).isNotNull();
    }
}

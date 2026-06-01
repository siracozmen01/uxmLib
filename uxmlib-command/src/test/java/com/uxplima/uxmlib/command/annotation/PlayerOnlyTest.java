package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/** Covers Player injection and @PlayerOnly guards building a valid tree. */
class PlayerOnlyTest {

    @Command(name = "p")
    static class PlayerCommand {
        // A Player parameter with no @Arg is injected as the sender, making the command player-only.
        @Subcommand("self")
        void self(org.bukkit.entity.Player player) {}

        @PlayerOnly
        @Subcommand("home")
        void home(Sender sender) {}

        // A Player WITH @Arg is a resolved argument, not the sender.
        @Subcommand("tp")
        void tp(Sender sender, @Arg("target") org.bukkit.entity.Player target) {}
    }

    @Test
    void injectedPlayerParameterBuildsWithoutAnArgNode() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new PlayerCommand());
        CommandNode<CommandSourceStack> self = node.getChild("self");
        assertThat(self).isNotNull();
        // No argument node: the player is injected, not parsed.
        assertThat(java.util.Objects.requireNonNull(self).getChildren()).isEmpty();
        assertThat(java.util.Objects.requireNonNull(self).getCommand()).isNotNull();
    }

    @Test
    void argPlayerStillBuildsAnArgumentNode() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new PlayerCommand());
        CommandNode<CommandSourceStack> tp = node.getChild("tp");
        assertThat(tp).isNotNull();
        assertThat(java.util.Objects.requireNonNull(tp).getChild("target")).isNotNull();
    }

    @Test
    void playerOnlyMethodBuildsCleanly() {
        assertThatCode(() -> AnnotatedCommands.buildNode(new PlayerCommand())).doesNotThrowAnyException();
    }
}

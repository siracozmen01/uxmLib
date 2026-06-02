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

/**
 * Verifies the native Bukkit arg types beyond player/world/material build argument nodes off their Paper
 * argument types: a {@link org.bukkit.Location}, an {@link org.bukkit.OfflinePlayer}, and a
 * {@link org.bukkit.Sound}. The node shape (and that registration accepts the type at all) is what proves the
 * resolvers are wired; their parse from a live source is exercised by Brigadier at runtime.
 */
class NativeResolversTest {

    @Command(name = "nat")
    static class NativeCommand {
        @Subcommand("tp")
        void tp(Sender sender, @Arg("where") org.bukkit.Location where) {}

        @Subcommand("seen")
        void seen(Sender sender, @Arg("who") org.bukkit.OfflinePlayer who) {}

        @Subcommand("play")
        void play(Sender sender, @Arg("sound") org.bukkit.Sound sound) {}
    }

    @Test
    void locationOfflinePlayerAndSoundBuildArgumentNodes() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new NativeCommand());
        assertThat(arg(node, "tp", "where")).isNotNull();
        assertThat(arg(node, "seen", "who")).isNotNull();
        assertThat(arg(node, "play", "sound")).isNotNull();
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
}

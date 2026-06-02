package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * End-to-end check that a rejected argument produces the typed per-argument error reply: an enum arg given a
 * bad value is dispatched through Brigadier's own dispatcher over the built node (no live server needed), and
 * the message the sender receives names the failing argument and the raw input. This proves the
 * {@link ArgBinder} &rarr; {@link ErrorContext} &rarr; {@link CommandExecutors} wiring, not just the record.
 */
class TypedErrorDispatchTest {

    enum Mode {
        SURVIVAL,
        CREATIVE
    }

    @Command(name = "game")
    static class GameCommand {
        @Subcommand("mode")
        void mode(Sender sender, @Arg("mode") Mode mode) {}
    }

    @Test
    void aRejectedEnumArgRepliesNamingTheArgumentAndInput() throws Exception {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new GameCommand());
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);

        CommandSender sender = mock(CommandSender.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);

        dispatcher.execute("game mode banana", source);

        ArgumentCaptor<Component> reply = ArgumentCaptor.forClass(Component.class);
        org.mockito.Mockito.verify(sender).sendMessage(reply.capture());
        String text = PlainTextComponentSerializer.plainText().serialize(reply.getValue());
        assertThat(text).contains("mode").contains("banana");
    }
}

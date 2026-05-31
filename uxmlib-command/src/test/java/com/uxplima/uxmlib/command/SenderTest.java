package com.uxplima.uxmlib.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

class SenderTest {

    @Test
    void unwrapsAPlayerSender() {
        Player player = mock(Player.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(player);

        Sender sender = Sender.of(source);

        assertThat(sender.isPlayer()).isTrue();
        assertThat(sender.player()).contains(player);
        assertThat(sender.bukkit()).isSameAs(player);
    }

    @Test
    void reportsNoPlayerForConsole() {
        CommandSender console = mock(CommandSender.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(console);

        Sender sender = Sender.of(source);

        assertThat(sender.isPlayer()).isFalse();
        assertThat(sender.player()).isEmpty();
    }

    @Test
    void sendsToTheUnderlyingSender() {
        CommandSender console = mock(CommandSender.class);
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(console);
        Component message = Component.text("hi");

        Sender.of(source).send(message);

        verify(console).sendMessage(message);
    }
}

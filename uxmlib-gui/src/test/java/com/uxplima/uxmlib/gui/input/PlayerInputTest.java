package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Exercises the chat backend of {@link PlayerInput} by feeding the chat handler a {@link PlayerMock} and a
 * message component (the same path {@code onChat} runs after extracting them from an {@code AsyncChatEvent}),
 * and smoke-tests the wiring of {@code install}, the chat/sign open paths, and quit cleanup. The sign editor
 * cannot be round-tripped under MockBukkit, so {@code SIGN} is opened and quit-cleaned only; the typed-line
 * routing it shares with chat is covered by {@link InputRouterTest}.
 */
class PlayerInputTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void chatMessageIsCapturedAndConsumed() {
        PlayerInput input = new PlayerInput(plugin);
        PlayerMock player = server.addPlayer();
        List<InputResult> results = new ArrayList<>();
        input.open(player, InputType.CHAT, Component.text("Type a name"), results::add);

        boolean consumed = input.handleChat(player, Component.text("Steve"));

        assertThat(consumed).isTrue();
        assertThat(results).containsExactly(new InputResult.Submitted("Steve"));
    }

    @Test
    void chatCancelKeywordAborts() {
        PlayerInput input = new PlayerInput(plugin);
        PlayerMock player = server.addPlayer();
        List<InputResult> results = new ArrayList<>();
        input.open(player, InputType.CHAT, Component.text("Type a name"), results::add);

        input.handleChat(player, Component.text("cancel"));

        assertThat(results).containsExactly(InputResult.Cancelled.INSTANCE);
    }

    @Test
    void chatFromAPlayerWithNoPendingRequestIsIgnored() {
        PlayerInput input = new PlayerInput(plugin);
        PlayerMock player = server.addPlayer();

        assertThat(input.handleChat(player, Component.text("idle chatter"))).isFalse();
    }

    @Test
    void quitCancelsAPendingChatRequest() {
        PlayerInput input = new PlayerInput(plugin);
        PlayerMock player = server.addPlayer();
        List<InputResult> results = new ArrayList<>();
        input.open(player, InputType.CHAT, Component.text("Type a name"), results::add);

        input.onQuit(new org.bukkit.event.player.PlayerQuitEvent(
                player, Component.empty(), org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(results).containsExactly(InputResult.Cancelled.INSTANCE);
        // The request is gone, so a later chat is not consumed.
        assertThat(input.handleChat(player, Component.text("late"))).isFalse();
    }

    @Test
    void installRegistersWithoutThrowing() {
        PlayerInput input = new PlayerInput(plugin);

        assertThatCode(input::install).doesNotThrowAnyException();
    }

    @Test
    void signPromptThatCannotOpenResolvesAsCancelledInsteadOfHanging() {
        // MockBukkit cannot drive the native sign editor, so opening it raises an UnimplementedOperation.
        // The backend must swallow that into a Cancelled result (never leaving the caller hanging) rather
        // than propagating. The happy-path typed-line routing is covered by InputRouterTest.
        PlayerInput input = new PlayerInput(plugin);
        PlayerMock player = server.addPlayer();
        List<InputResult> results = new ArrayList<>();

        assertThatCode(() -> input.open(player, InputType.SIGN, Component.text("Enter value"), results::add))
                .doesNotThrowAnyException();

        assertThat(results).containsExactly(InputResult.Cancelled.INSTANCE);
    }
}

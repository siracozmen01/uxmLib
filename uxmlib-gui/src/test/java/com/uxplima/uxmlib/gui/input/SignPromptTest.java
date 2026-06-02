package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.block.Block;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers that the transient sign block {@link SignPrompt} writes into the world is always restored — by
 * {@link SignPrompt#restore} for a single player and {@link SignPrompt#restoreAll} on teardown. The native
 * sign editor cannot be opened under MockBukkit (the block is placed first, then {@code openSign} throws),
 * so each test opens inside a try/catch and asserts the world is put back the way it was.
 */
class SignPromptTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static void openIgnoringEditor(SignPrompt prompt, PlayerMock player) {
        try {
            prompt.open(player, Component.text("Enter value"));
        } catch (RuntimeException ignored) {
            // The native editor cannot open under MockBukkit; the block has already been placed by then.
        }
    }

    @Test
    void restorePutsBackTheReplacedBlock() {
        SignPrompt prompt = new SignPrompt();
        PlayerMock player = server.addPlayer();
        Block block = player.getLocation().getBlock();
        Material original = block.getType();

        openIgnoringEditor(prompt, player);
        assertThat(block.getType()).isEqualTo(Material.OAK_SIGN); // a real sign was written into the world

        prompt.restore(player);

        assertThat(block.getType()).isEqualTo(original); // the world is back the way it was
    }

    @Test
    void restoreAllPutsBackEveryStillPendingBlock() {
        SignPrompt prompt = new SignPrompt();
        PlayerMock a = server.addPlayer();
        PlayerMock b = server.addPlayer();
        Block blockA = a.getLocation().getBlock();
        Block blockB = b.getLocation().getBlock();
        Material originalA = blockA.getType();
        Material originalB = blockB.getType();

        openIgnoringEditor(prompt, a);
        openIgnoringEditor(prompt, b);

        prompt.restoreAll();

        assertThat(blockA.getType()).isEqualTo(originalA);
        assertThat(blockB.getType()).isEqualTo(originalB);
    }

    @Test
    void restoreIsIdempotentWhenNothingIsPending() {
        SignPrompt prompt = new SignPrompt();
        PlayerMock player = server.addPlayer();

        // No prompt was opened for this player, so restore must be a harmless no-op.
        org.assertj.core.api.Assertions.assertThatCode(() -> prompt.restore(player))
                .doesNotThrowAnyException();
    }
}

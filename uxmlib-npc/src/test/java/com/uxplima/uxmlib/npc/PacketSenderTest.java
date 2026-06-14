package com.uxplima.uxmlib.npc;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Smoke test for the packet sender. MockBukkit's {@link PlayerMock} has no real Netty channel, so the
 * {@link ChannelResolver} resolves to empty; the contract under test is that sending to such a player is a
 * graceful no-op rather than an error. Argument-validation guards are asserted directly.
 */
class PacketSenderTest {

    private ServerMock server;
    private final PacketSender sender = new PacketSender(new ChannelResolver());

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sendingToAPlayerWithNoResolvableChannelIsANoOp() {
        PlayerMock player = server.addPlayer();
        assertThatCode(() -> sender.send(player, new Object())).doesNotThrowAnyException();
    }

    @Test
    void constructorRejectsNullResolver() {
        assertThatNullPointerException().isThrownBy(() -> new PacketSender(nullResolver()));
    }

    @Test
    void sendRejectsNullArguments() {
        PlayerMock player = server.addPlayer();
        assertThatNullPointerException().isThrownBy(() -> sender.send(nullPlayer(), new Object()));
        assertThatNullPointerException().isThrownBy(() -> sender.send(player, nullPacket()));
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the constructor guard fires.
    private static ChannelResolver nullResolver() {
        return null;
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the entry-point guard fires.
    private static org.bukkit.entity.Player nullPlayer() {
        return null;
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the entry-point guard fires.
    private static Object nullPacket() {
        return null;
    }
}

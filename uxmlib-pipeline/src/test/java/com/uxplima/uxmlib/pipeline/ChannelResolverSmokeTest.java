package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;

import io.netty.channel.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Smoke test for the reflective channel resolver. MockBukkit's {@link PlayerMock} has no {@code getHandle()}
 * and no real Netty channel, so the only contract a unit test can assert is the one that matters in
 * production when a layout is unexpected: the resolver <b>fails gracefully</b>: it returns empty and never
 * throws. The real CraftPlayer walk is exercised only on a live server (documented in the package-info).
 */
class ChannelResolverSmokeTest {

    private ServerMock server;
    private final ChannelResolver resolver = new ChannelResolver();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvingAMockPlayerReturnsEmptyWithoutThrowing() {
        PlayerMock player = server.addPlayer();
        Optional<Channel> channel = resolver.resolve(player);
        assertThat(channel).isEmpty();
    }

    @Test
    void resolveNeverThrowsEvenAcrossRepeatedCalls() {
        PlayerMock player = server.addPlayer();
        assertThatCode(() -> {
                    resolver.resolve(player);
                    resolver.resolve(player);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void resolveRejectsNullPlayer() {
        assertThatNullPointerException().isThrownBy(() -> resolver.resolve(nullPlayer()));
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the entry-point guard fires.
    private static org.bukkit.entity.Player nullPlayer() {
        return null;
    }
}

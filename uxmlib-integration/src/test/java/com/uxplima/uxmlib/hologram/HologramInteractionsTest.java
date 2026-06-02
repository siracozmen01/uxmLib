package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Tests the pure reach/world gate that {@link HologramInteractions} applies before firing a click. The
 * gate is a static predicate so it can be exercised without spawning entities or pumping events.
 */
class HologramInteractionsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void allowsClickWithinReachInSameWorld() {
        World world = server.addSimpleWorld("reach");
        Location player = new Location(world, 0, 64, 0);
        Location holo = new Location(world, 3, 64, 4); // 9 + 16 = 25 <= 36
        assertThat(HologramInteractions.withinReach(player, holo)).isTrue();
    }

    @Test
    void rejectsClickBeyondReachInSameWorld() {
        World world = server.addSimpleWorld("far");
        Location player = new Location(world, 0, 64, 0);
        Location holo = new Location(world, 6, 64, 6); // 36 + 36 = 72 > 36
        assertThat(HologramInteractions.withinReach(player, holo)).isFalse();
    }

    @Test
    void rejectsClickAcrossDifferentWorlds() {
        World a = server.addSimpleWorld("a");
        World b = server.addSimpleWorld("b");
        Location player = new Location(a, 0, 64, 0);
        Location holo = new Location(b, 0, 64, 0); // same coords, different world
        // distanceSquared would throw across worlds, so the gate must short-circuit on the world check.
        assertThat(HologramInteractions.withinReach(player, holo)).isFalse();
    }

    @Test
    void rejectsClickWhenAWorldIsMissing() {
        Location player = new Location(null, 0, 64, 0);
        Location holo = new Location(null, 1, 64, 1);
        assertThat(HologramInteractions.withinReach(player, holo)).isFalse();
    }

    @Test
    void quitEvictsThePlayersDebounceEntry() {
        Plugin plugin = MockBukkit.createMockPlugin();
        HologramInteractions interactions = new HologramInteractions(plugin);
        interactions.install();
        PlayerMock player = server.addPlayer();
        interactions.recordClickForTest(player.getUniqueId());
        assertThat(interactions.trackedPlayerCount()).isEqualTo(1);

        // disconnect() dispatches a real PlayerQuitEvent through the plugin manager, exercising the registered
        // listener. Without the quit handler the timestamp would linger forever, one entry per ex-player.
        player.disconnect();

        assertThat(interactions.trackedPlayerCount()).isZero();
    }
}

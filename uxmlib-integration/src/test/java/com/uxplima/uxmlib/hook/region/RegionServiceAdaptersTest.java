package com.uxplima.uxmlib.hook.region;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Smoke-tests the real WorldGuard and Towny adapters under MockBukkit, which has neither plugin: each
 * {@code find()} must take the present-guard path and report empty rather than throwing, proving the
 * adapter classes load and never touch their third-party API when the plugin is absent. The query methods
 * (testState / getCachePermission) need a running WorldGuard / Towny instance, so they are exercised on a
 * live server, not here.
 */
class RegionServiceAdaptersTest {

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
    void worldGuardFindIsEmptyWhenAbsent() {
        assertThat(WorldGuardRegionService.find()).isEmpty();
    }

    @Test
    void townyFindIsEmptyWhenAbsent() {
        assertThat(TownyRegionService.find()).isEmpty();
    }

    @Test
    void worldGuardQueriesReturnSafeDefaultsForAWorldlessLocationWithoutTouchingWorldEdit() {
        // A Bukkit Location can legally carry a null world; adapting it to WorldEdit would NPE deep inside
        // BukkitAdapter. The guard must short-circuit every query before any com.sk89q call, so these run
        // green here even though MockBukkit has no WorldGuard instance.
        WorldGuardRegionService service = new WorldGuardRegionService();
        PlayerMock player = server.addPlayer();
        Location worldless = new Location(null, 1, 64, 2);

        assertThat(service.canBuild(player, worldless)).isTrue();
        assertThat(service.canInteract(player, worldless)).isTrue();
        assertThat(service.regionsAt(worldless)).isEmpty();
        assertThat(service.isWilderness(worldless)).isTrue();
    }

    @Test
    void registryOverTheRealAdaptersHasNoProviderWhenBothAbsent() {
        RegionHooks hooks = new RegionHooks();
        WorldGuardRegionService.find().ifPresent(hooks::register);
        TownyRegionService.find().ifPresent(hooks::register);

        assertThat(hooks.hasProvider()).isFalse();
        assertThat(hooks.active()).isEmpty();
    }
}

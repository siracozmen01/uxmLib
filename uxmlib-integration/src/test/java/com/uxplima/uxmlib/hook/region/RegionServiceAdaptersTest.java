package com.uxplima.uxmlib.hook.region;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Smoke-tests the real WorldGuard and Towny adapters under MockBukkit, which has neither plugin: each
 * {@code find()} must take the present-guard path and report empty rather than throwing, proving the
 * adapter classes load and never touch their third-party API when the plugin is absent. The query methods
 * (testState / getCachePermission) need a running WorldGuard / Towny instance, so they are exercised on a
 * live server, not here.
 */
class RegionServiceAdaptersTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
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
    void registryOverTheRealAdaptersHasNoProviderWhenBothAbsent() {
        RegionHooks hooks = new RegionHooks();
        WorldGuardRegionService.find().ifPresent(hooks::register);
        TownyRegionService.find().ifPresent(hooks::register);

        assertThat(hooks.hasProvider()).isFalse();
        assertThat(hooks.active()).isEmpty();
    }
}

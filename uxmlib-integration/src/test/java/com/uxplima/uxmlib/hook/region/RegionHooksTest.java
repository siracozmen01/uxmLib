package com.uxplima.uxmlib.hook.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.Test;

/**
 * Verifies the registry's first-present selection against a fake {@link RegionService}: an absent candidate
 * is skipped, the earliest present one wins, and an all-absent registry degrades to empty.
 */
class RegionHooksTest {

    /** A pure fake region service whose availability and answers are fixed at construction. */
    private static final class FakeRegionService implements RegionService {
        private final String name;
        private final boolean available;

        FakeRegionService(String name, boolean available) {
            this.name = name;
            this.available = available;
        }

        @Override
        public String pluginName() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean canBuild(Player player, Location location) {
            return true;
        }

        @Override
        public boolean canInteract(Player player, Location location) {
            return true;
        }

        @Override
        public Set<String> regionsAt(Location location) {
            return Set.of(name);
        }

        @Override
        public boolean isWilderness(Location location) {
            return false;
        }
    }

    @Test
    void activeIsEmptyWhenNoCandidates() {
        assertThat(new RegionHooks().active()).isEmpty();
        assertThat(new RegionHooks().hasProvider()).isFalse();
    }

    @Test
    void activeIsEmptyWhenEveryCandidateAbsent() {
        RegionHooks hooks = new RegionHooks()
                .register(new FakeRegionService("WorldGuard", false))
                .register(new FakeRegionService("Towny", false));

        assertThat(hooks.active()).isEmpty();
        assertThat(hooks.hasProvider()).isFalse();
    }

    @Test
    void activeReturnsTheFirstPresentCandidateInRegistrationOrder() {
        RegionHooks hooks = new RegionHooks()
                .register(new FakeRegionService("WorldGuard", false))
                .register(new FakeRegionService("Towny", true))
                .register(new FakeRegionService("Lands", true));

        assertThat(hooks.active()).isPresent();
        assertThat(hooks.active().orElseThrow().pluginName()).isEqualTo("Towny");
        assertThat(hooks.hasProvider()).isTrue();
    }

    @Test
    void earlierRegistrationWinsWhenSeveralArePresent() {
        RegionHooks hooks = new RegionHooks()
                .register(new FakeRegionService("WorldGuard", true))
                .register(new FakeRegionService("Towny", true));

        assertThat(hooks.active().orElseThrow().pluginName()).isEqualTo("WorldGuard");
    }
}

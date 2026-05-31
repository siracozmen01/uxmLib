package com.uxplima.uxmlib.hook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Tests the load-without-the-plugin invariant: with no soft-dependency installed, the hooks report
 * absent and degrade gracefully rather than throwing.
 */
class HooksTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reportsAbsentPluginsAsNotPresent() {
        assertThat(Hooks.isPresent("PlaceholderAPI")).isFalse();
        assertThat(Hooks.isPresent("Vault")).isFalse();
    }

    @Test
    void placeholdersReturnTextUnchangedWhenApiAbsent() {
        var player = MockBukkit.getMock().addPlayer();
        assertThat(Placeholders.isAvailable()).isFalse();
        assertThat(Placeholders.apply(player, "hi %player_name%")).isEqualTo("hi %player_name%");
    }

    @Test
    void vaultEconomyIsEmptyWhenVaultAbsent() {
        assertThat(VaultEconomy.find()).isEmpty();
    }
}

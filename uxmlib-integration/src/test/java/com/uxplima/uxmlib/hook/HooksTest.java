package com.uxplima.uxmlib.hook;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmlib.hook.economy.VaultEconomy;
import com.uxplima.uxmlib.hook.economy.VaultUnlockedEconomy;
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
        assertThat(PlaceholderApi.isAvailable()).isFalse();
        assertThat(PlaceholderApi.apply(player, "hi %player_name%")).isEqualTo("hi %player_name%");
    }

    @Test
    void vaultEconomyIsEmptyWhenVaultAbsent() {
        assertThat(VaultEconomy.find()).isEmpty();
    }

    @Test
    void vaultUnlockedEconomyIsEmptyWhenNoProviderIsRegistered() {
        assertThat(VaultUnlockedEconomy.find()).isEmpty();
    }

    /**
     * The guard the vault2 hook used to carry. It is kept as a test rather than as a comment, because it is
     * the reason that hook was dead: VaultUnlocked declares itself as {@code Vault}, so no server has ever
     * reported a plugin under this name, and a name guard would return empty on every one of them.
     */
    @Test
    void noServerRunsAPluginCalledVaultUnlocked() {
        assertThat(Hooks.isPresent("VaultUnlocked")).isFalse();
    }
}

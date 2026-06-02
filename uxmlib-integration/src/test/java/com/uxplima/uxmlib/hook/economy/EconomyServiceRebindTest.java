package com.uxplima.uxmlib.hook.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Exercises the Vault-backed path end to end: a fake {@link Economy} provider drives the bridge's
 * format/currency delegation, and {@link EconomyServiceListener} rebinds the {@link RebindingEconomyBridge}
 * when that provider registers and unregisters its service after startup. The fake Vault plugin satisfies
 * {@code Hooks.isPresent("Vault")}; everything else is the real production resolution path.
 */
class EconomyServiceRebindTest {

    private ServerMock server;
    private Plugin vault;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // A plugin literally named "Vault" so the present-guard in VaultEconomy.find() passes.
        vault = MockBukkit.createMockPlugin("Vault");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theBridgeDelegatesFormatAndCurrencyToTheRegisteredProvider() {
        register(fakeEconomy());

        EconomyBridge bridge = EconomyBridge.find().orElseThrow();

        assertThat(bridge.isPresent()).isTrue();
        assertThat(bridge.format(1234.0d)).isEqualTo("$1,234.00");
        assertThat(bridge.currencyNameSingular()).isEqualTo("Dollar");
        assertThat(bridge.currencyNamePlural()).isEqualTo("Dollars");
        // Vault exposes no symbol, so currencySymbol() falls back to the singular name.
        assertThat(bridge.currencySymbol()).isEqualTo("Dollar");
    }

    @Test
    void aProviderRegisteredAfterStartupIsPickedUpOnTheServiceRegisterEvent() {
        RebindingEconomyBridge bridge = new RebindingEconomyBridge();
        EconomyServiceListener listener = new EconomyServiceListener(bridge);
        // No provider yet: the bridge sits on the dummy economy.
        assertThat(bridge.isPresent()).isFalse();

        Economy economy = fakeEconomy();
        RegisteredServiceProvider<Economy> registration = register(economy);
        listener.onServiceRegister(new ServiceRegisterEvent(registration));

        assertThat(bridge.isPresent()).isTrue();
        assertThat(bridge.format(1234.0d)).isEqualTo("$1,234.00");
    }

    @Test
    void aProviderUnregisteredAtRuntimeDropsTheBridgeBackToTheDummy() {
        RebindingEconomyBridge bridge = new RebindingEconomyBridge();
        EconomyServiceListener listener = new EconomyServiceListener(bridge);
        Economy economy = fakeEconomy();
        RegisteredServiceProvider<Economy> registration = register(economy);
        listener.onServiceRegister(new ServiceRegisterEvent(registration));
        assertThat(bridge.isPresent()).isTrue();

        server.getServicesManager().unregister(Economy.class, economy);
        listener.onServiceUnregister(new ServiceUnregisterEvent(registration));

        assertThat(bridge.isPresent()).isFalse();
    }

    @Test
    void anUnrelatedServiceRegistrationDoesNotRebindTheBridge() {
        RebindingEconomyBridge bridge = new RebindingEconomyBridge();
        EconomyServiceListener listener = new EconomyServiceListener(bridge);
        EconomyBridge before = bridge.current();

        // A non-economy service: the listener must ignore it by class-name filter.
        RegisteredServiceProvider<Plugin> unrelated =
                new RegisteredServiceProvider<>(Plugin.class, vault, ServicePriority.Normal, vault);
        listener.onServiceRegister(new ServiceRegisterEvent(unrelated));

        assertThat(bridge.current()).isSameAs(before);
        assertThat(bridge.isPresent()).isFalse();
    }

    private RegisteredServiceProvider<Economy> register(Economy economy) {
        ServicesManager services = server.getServicesManager();
        services.register(Economy.class, economy, vault, ServicePriority.Normal);
        RegisteredServiceProvider<Economy> registration = services.getRegistration(Economy.class);
        return registration;
    }

    private static Economy fakeEconomy() {
        Economy economy = mock(Economy.class);
        when(economy.isEnabled()).thenReturn(true);
        when(economy.getName()).thenReturn("Fake");
        when(economy.format(anyDouble())).thenAnswer(invocation -> {
            double amount = invocation.getArgument(0);
            return "$" + String.format("%,.2f", amount);
        });
        when(economy.currencyNameSingular()).thenReturn("Dollar");
        when(economy.currencyNamePlural()).thenReturn("Dollars");
        when(economy.getBalance(any(OfflinePlayer.class))).thenReturn(500.0d);
        when(economy.withdrawPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(0.0d, 0.0d, ResponseType.SUCCESS, null));
        when(economy.depositPlayer(any(OfflinePlayer.class), anyDouble()))
                .thenReturn(new EconomyResponse(0.0d, 0.0d, ResponseType.SUCCESS, null));
        return economy;
    }
}

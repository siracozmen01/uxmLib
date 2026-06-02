package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;

import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mockito;

/**
 * Covers the one-time menu-listener install seam. {@link Guis#install} registers exactly one
 * {@link GuiListener} and is idempotent — a second call must not register a second listener, or every menu
 * event would be handled twice. {@link Guis#uninstall} removes the listener (and clears the animation
 * registry) so a plugin can tear down cleanly on disable or reinstall on reload.
 */
class GuisTest {

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall(); // reset the static install state so tests do not leak into each other
        MockBukkit.unmock();
    }

    private static long registeredGuiListeners() {
        return Arrays.stream(InventoryCloseEvent.getHandlerList().getRegisteredListeners())
                .filter(registration -> registration.getListener() instanceof GuiListener)
                .count();
    }

    @Test
    void installRegistersExactlyOneListener() {
        Guis.install(plugin);

        assertThat(registeredGuiListeners()).isEqualTo(1);
        assertThat(Guis.isInstalled()).isTrue();
    }

    @Test
    void installIsIdempotent() {
        Guis.install(plugin);
        Guis.install(plugin);

        assertThat(registeredGuiListeners()).isEqualTo(1);
    }

    @Test
    void uninstallRemovesTheListener() {
        Guis.install(plugin);

        Guis.uninstall();

        assertThat(registeredGuiListeners()).isZero();
        assertThat(Guis.isInstalled()).isFalse();
    }

    @Test
    void installAfterUninstallReinstalls() {
        Guis.install(plugin);
        Guis.uninstall();

        Guis.install(plugin);

        assertThat(registeredGuiListeners()).isEqualTo(1);
    }

    @Test
    void uninstallWithoutInstallIsHarmless() {
        assertThatCode(Guis::uninstall).doesNotThrowAnyException();
        assertThat(registeredGuiListeners()).isZero();
    }

    @Test
    void installWithSchedulerSetsTheAnimationRegistryAndUninstallClearsIt() {
        Guis.install(plugin, Mockito.mock(Scheduler.class));
        assertThat(Guis.registry()).isNotNull();
        assertThat(registeredGuiListeners()).isEqualTo(1);

        Guis.uninstall();

        assertThat(Guis.registry()).isNull();
        assertThat(registeredGuiListeners()).isZero();
    }
}

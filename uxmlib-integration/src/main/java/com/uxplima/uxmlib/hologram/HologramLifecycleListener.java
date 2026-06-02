package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * The single Bukkit listener behind the lifecycle SPI. It translates the four player events into the
 * manager's {@code dispatch*} fan-out, which forwards each one to every registered {@link HologramLifecycle}
 * widget and to the built-in viewer invalidation. Owning exactly one listener here is what lets a widget hook
 * join/quit/respawn/world-change just by registering, without wiring events of its own.
 *
 * <p>Registered by {@link HologramManager#installLifecycleListener}.
 */
final class HologramLifecycleListener implements Listener {

    private final HologramManager manager;

    HologramLifecycleListener(HologramManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        manager.dispatchJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        manager.dispatchQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onRespawn(PlayerRespawnEvent event) {
        manager.dispatchRespawn(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onWorldChange(PlayerChangedWorldEvent event) {
        manager.dispatchWorldChange(event.getPlayer().getUniqueId());
    }
}

package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Keeps each hologram's per-viewer cache honest across the player events that would otherwise leave it
 * holding a stale or departed UUID. On quit the player is gone, so its UUID is dropped from every tracked
 * hologram; on respawn and world-change the player's visibility may no longer hold (different world, moved
 * far away), so its cache entry is invalidated and the next show/hide pass re-establishes the truth.
 *
 * <p>Owned and registered by {@link HologramManager#installLifecycleListener}; it simply forwards the
 * affected player's UUID to {@link HologramManager#invalidateViewer}.
 */
final class HologramLifecycleListener implements Listener {

    private final HologramManager manager;

    HologramLifecycleListener(HologramManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        manager.invalidateViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onRespawn(PlayerRespawnEvent event) {
        manager.invalidateViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onWorldChange(PlayerChangedWorldEvent event) {
        manager.invalidateViewer(event.getPlayer().getUniqueId());
    }
}

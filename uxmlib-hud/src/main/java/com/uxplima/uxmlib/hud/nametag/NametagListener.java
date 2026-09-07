package com.uxplima.uxmlib.hud.nametag;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drops a quitting player's name from the registry, so a team is not left behind for someone who has gone.
 * Owned and registered by the consumer, the way {@link com.uxplima.uxmlib.hud.HudListener} is.
 */
public final class NametagListener implements Listener {

    private final Nametags registry;

    public NametagListener(Nametags registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        registry.forget(event.getPlayer().getUniqueId());
    }
}

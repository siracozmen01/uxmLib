package com.uxplima.uxmlib.hud;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Releases a quitting player's HUD state immediately rather than on the next tick. It forwards the departing
 * player's UUID to {@link BossBarManager#hide} and {@link ActionBarManager#clear}, mirroring how
 * {@link com.uxplima.uxmlib.hud.scoreboard.SidebarListener} drops a sidebar on quit. Owned and registered by
 * the consumer alongside the managers it serves.
 */
public final class HudListener implements Listener {

    private final BossBarManager bossBars;
    private final ActionBarManager actionBars;

    public HudListener(BossBarManager bossBars, ActionBarManager actionBars) {
        this.bossBars = Objects.requireNonNull(bossBars, "bossBars");
        this.actionBars = Objects.requireNonNull(actionBars, "actionBars");
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        bossBars.hide(id);
        actionBars.clear(id);
    }
}

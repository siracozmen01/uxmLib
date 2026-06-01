package com.uxplima.uxmlib.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

/**
 * A spawned hologram backed by a native {@link TextDisplay} entity. Update its text in place, move it,
 * control who can see it, or remove it; the mutating calls must run on the entity's region thread
 * (Folia), so route them through your scheduler. Per-viewer visibility uses Paper's native
 * {@code show/hideEntity}, not packets.
 */
public interface Hologram {

    /** Replace the displayed text. */
    void setText(Component text);

    /**
     * Move the hologram to {@code to}, interpolating over {@code interpolationTicks} so current viewers see
     * smooth motion instead of a jump (native {@code setTeleportDuration}). Pass 0 for an instant move.
     */
    void moveTo(Location to, int interpolationTicks);

    /** Make this hologram visible only to explicitly shown players (native per-viewer visibility). */
    void restrictToViewers();

    /** Show the hologram to {@code viewer} via {@code plugin} (only meaningful after restriction). */
    void show(Plugin plugin, Player viewer);

    /** Hide the hologram from {@code viewer} via {@code plugin}. */
    void hide(Plugin plugin, Player viewer);

    /** Whether {@code viewer} is in the allowed-viewer set. */
    boolean isVisibleTo(Player viewer);

    /** Despawn the backing display entity. Safe to call more than once. */
    void remove();

    /** The backing display entity. */
    TextDisplay entity();
}

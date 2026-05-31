package com.uxplima.uxmlib.hologram;

import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;

/**
 * A spawned hologram backed by a native {@link TextDisplay} entity. Update its text in place or remove
 * it; both must run on the entity's region thread (Folia), so route them through your scheduler.
 */
public interface Hologram {

    /** Replace the displayed text. */
    void setText(Component text);

    /** Despawn the backing display entity. Safe to call more than once. */
    void remove();

    /** The backing display entity. */
    TextDisplay entity();
}

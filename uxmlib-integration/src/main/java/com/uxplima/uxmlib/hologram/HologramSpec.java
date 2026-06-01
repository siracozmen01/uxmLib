package com.uxplima.uxmlib.hologram;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

/**
 * The immutable description of a hologram: its text lines and its {@link Appearance}. Building this is
 * pure (no world, no entity), so it can be assembled and asserted without a running server;
 * {@link Holograms} turns it into a live {@link org.bukkit.entity.TextDisplay}. Because styling lives in
 * the spec, a spec round-trips losslessly — what you build is exactly what spawns.
 */
public record HologramSpec(List<Component> lines, Appearance appearance) {

    public HologramSpec {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(appearance, "appearance");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram needs at least one line");
        }
        lines = List.copyOf(lines);
    }

    /** A spec with the given lines and the default appearance. */
    public static HologramSpec of(List<Component> lines) {
        return new HologramSpec(lines, Appearance.DEFAULT);
    }

    /** The lines joined with newlines into a single component, as a {@code TextDisplay} renders them. */
    public Component asText() {
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
    }
}

package com.uxplima.uxmlib.hologram;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Display;

import net.kyori.adventure.text.Component;

/**
 * The immutable description of a hologram: its text lines and how the display faces the viewer. Building
 * this is pure (no world, no entity), so it can be assembled and asserted without a running server;
 * {@link Holograms} turns it into a live {@link org.bukkit.entity.TextDisplay}.
 */
public record HologramSpec(List<Component> lines, Display.Billboard billboard, boolean seeThrough) {

    public HologramSpec {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(billboard, "billboard");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram needs at least one line");
        }
        lines = List.copyOf(lines);
    }

    /** The lines joined with newlines into a single component, as a {@code TextDisplay} renders them. */
    public Component asText() {
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
    }
}

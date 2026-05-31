package com.uxplima.uxmlib.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;

/**
 * Builds and spawns native-Display holograms. Configure lines and appearance with {@link #builder()},
 * then either inspect the {@link HologramSpec} (pure, for tests) or {@link Builder#spawnAt(Location)}
 * the live entity. Spawning uses {@code World.spawn(loc, TextDisplay.class, initializer)} so the text is
 * set before the entity is added to the world (no one-tick flash). Call {@code spawnAt} on the target
 * region's thread; on Folia, schedule it.
 */
public final class Holograms {

    private Holograms() {}

    /** Start configuring a hologram. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for a hologram's content and appearance. */
    public static final class Builder {
        private final List<Component> lines = new ArrayList<>();
        private Display.Billboard billboard = Display.Billboard.CENTER;
        private boolean seeThrough;

        private Builder() {}

        /** Append a text line. */
        public Builder line(Component line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        /** How the display faces the viewer; {@link Display.Billboard#CENTER} (always faces) by default. */
        public Builder billboard(Display.Billboard billboard) {
            this.billboard = Objects.requireNonNull(billboard, "billboard");
            return this;
        }

        /** Whether the text shows through blocks. */
        public Builder seeThrough(boolean seeThrough) {
            this.seeThrough = seeThrough;
            return this;
        }

        /** The immutable specification, for inspection or reuse. Requires at least one line. */
        public HologramSpec spec() {
            return new HologramSpec(lines, billboard, seeThrough);
        }

        /** Spawn the hologram at {@code location}. Must run on that location's region thread. */
        public Hologram spawnAt(Location location) {
            Objects.requireNonNull(location, "location");
            HologramSpec spec = spec();
            Objects.requireNonNull(location.getWorld(), "location world");
            TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.text(spec.asText());
                entity.setBillboard(spec.billboard());
                entity.setSeeThrough(spec.seeThrough());
            });
            return new DisplayHologram(display);
        }
    }

    /** A {@link Hologram} backed by a live {@link TextDisplay}. */
    private static final class DisplayHologram implements Hologram {
        private final TextDisplay display;

        private DisplayHologram(TextDisplay display) {
            this.display = display;
        }

        @Override
        public void setText(Component text) {
            display.text(Objects.requireNonNull(text, "text"));
        }

        @Override
        public void remove() {
            display.remove();
        }

        @Override
        public TextDisplay entity() {
            return display;
        }
    }
}

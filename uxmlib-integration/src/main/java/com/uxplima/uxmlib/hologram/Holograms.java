package com.uxplima.uxmlib.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

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

    /** Start configuring a text hologram. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Spawn a floating item display at {@code location}. Must run on that location's region thread; the
     * returned {@link ItemDisplay} is the live entity (remove it with {@link org.bukkit.entity.Entity#remove()}).
     */
    public static ItemDisplay spawnItem(Location location, ItemStack item) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(location.getWorld(), "location world");
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> entity.setItemStack(item));
    }

    /** Spawn a floating block display at {@code location}. Must run on that location's region thread. */
    public static BlockDisplay spawnBlock(Location location, BlockData block) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(location.getWorld(), "location world");
        return location.getWorld().spawn(location, BlockDisplay.class, entity -> entity.setBlock(block));
    }

    /** Fluent builder for a hologram's content and appearance. */
    public static final class Builder {
        private final List<Component> lines = new ArrayList<>();
        private Appearance appearance = Appearance.DEFAULT;

        private Builder() {}

        /** Append a text line. */
        public Builder line(Component line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        /** How the display faces the viewer; {@link Display.Billboard#CENTER} (always faces) by default. */
        public Builder billboard(Display.Billboard billboard) {
            appearance = appearance.withBillboard(Objects.requireNonNull(billboard, "billboard"));
            return this;
        }

        /** Whether the text shows through blocks. */
        public Builder seeThrough(boolean seeThrough) {
            appearance = appearance.withSeeThrough(seeThrough);
            return this;
        }

        /** Give the text a glowing outline in {@code color}. */
        public Builder glow(Color color) {
            appearance = appearance.withGlow(Objects.requireNonNull(color, "color"));
            return this;
        }

        /** Set the text-panel background colour (supports ARGB). */
        public Builder background(Color color) {
            appearance = appearance.withBackground(Objects.requireNonNull(color, "color"));
            return this;
        }

        /** Set the text opacity (0–255 as a signed byte; {@code -1} is fully opaque). */
        public Builder textOpacity(byte opacity) {
            appearance = appearance.withTextOpacity(opacity);
            return this;
        }

        /** Set the maximum line width in pixels before wrapping. */
        public Builder lineWidth(int width) {
            appearance = appearance.withLineWidth(width);
            return this;
        }

        /** Whether the text casts a shadow. */
        public Builder textShadow(boolean shadow) {
            appearance = appearance.withTextShadow(shadow);
            return this;
        }

        /** Set how far away the hologram stays visible (a view-range multiplier). */
        public Builder viewRange(float range) {
            appearance = appearance.withViewRange(range);
            return this;
        }

        /** Override the block/sky light levels the text is rendered at. */
        public Builder brightness(Display.Brightness brightness) {
            appearance = appearance.withBrightness(Objects.requireNonNull(brightness, "brightness"));
            return this;
        }

        /** The immutable specification, for inspection or reuse. Requires at least one line. */
        public HologramSpec spec() {
            return new HologramSpec(lines, appearance);
        }

        /** Spawn the hologram at {@code location}. Must run on that location's region thread. */
        public Hologram spawnAt(Location location) {
            Objects.requireNonNull(location, "location");
            return Holograms.spawn(spec(), location);
        }
    }

    /** Spawn {@code spec} at {@code location} as a tracked, manager-free {@link Hologram}. */
    static Hologram spawn(HologramSpec spec, Location location) {
        Objects.requireNonNull(location.getWorld(), "location world");
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(spec.asText());
            spec.appearance().applyTo(entity);
            Markers.stamp(entity);
        });
        return new DisplayHologram(display);
    }
}

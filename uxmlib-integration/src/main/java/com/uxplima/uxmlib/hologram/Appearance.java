package com.uxplima.uxmlib.hologram;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import org.jspecify.annotations.Nullable;

/**
 * The visual styling of a text hologram, separate from its text so {@link HologramSpec} stays small. Every
 * field maps to a native {@link TextDisplay} setter; a {@code null} leaves Paper's default. Build one
 * fluently with {@link #DEFAULT} and the {@code with*} methods, or let {@link Holograms.Builder} set them.
 */
public record Appearance(
        Display.Billboard billboard,
        boolean seeThrough,
        @Nullable Color glow,
        @Nullable Color background,
        @Nullable Byte textOpacity,
        @Nullable Integer lineWidth,
        boolean textShadow,
        @Nullable Float viewRange,
        Display.@Nullable Brightness brightness) {

    /** The default appearance: centred billboard, no overrides. */
    public static final Appearance DEFAULT =
            new Appearance(Display.Billboard.CENTER, false, null, null, null, null, false, null, null);

    public Appearance withBillboard(Display.Billboard value) {
        return new Appearance(
                value, seeThrough, glow, background, textOpacity, lineWidth, textShadow, viewRange, brightness);
    }

    public Appearance withSeeThrough(boolean value) {
        return new Appearance(
                billboard, value, glow, background, textOpacity, lineWidth, textShadow, viewRange, brightness);
    }

    public Appearance withGlow(@Nullable Color value) {
        return new Appearance(
                billboard, seeThrough, value, background, textOpacity, lineWidth, textShadow, viewRange, brightness);
    }

    public Appearance withBackground(@Nullable Color value) {
        return new Appearance(
                billboard, seeThrough, glow, value, textOpacity, lineWidth, textShadow, viewRange, brightness);
    }

    public Appearance withTextOpacity(@Nullable Byte value) {
        return new Appearance(
                billboard, seeThrough, glow, background, value, lineWidth, textShadow, viewRange, brightness);
    }

    public Appearance withLineWidth(@Nullable Integer value) {
        return new Appearance(
                billboard, seeThrough, glow, background, textOpacity, value, textShadow, viewRange, brightness);
    }

    public Appearance withTextShadow(boolean value) {
        return new Appearance(
                billboard, seeThrough, glow, background, textOpacity, lineWidth, value, viewRange, brightness);
    }

    public Appearance withViewRange(@Nullable Float value) {
        return new Appearance(
                billboard, seeThrough, glow, background, textOpacity, lineWidth, textShadow, value, brightness);
    }

    public Appearance withBrightness(Display.@Nullable Brightness value) {
        return new Appearance(
                billboard, seeThrough, glow, background, textOpacity, lineWidth, textShadow, viewRange, value);
    }

    /** Apply every set field to {@code display}, leaving unset (null/false) fields at Paper's default. */
    void applyTo(TextDisplay display) {
        display.setBillboard(billboard);
        display.setSeeThrough(seeThrough);
        display.setShadowed(textShadow);
        if (glow != null) {
            display.setGlowing(true);
            display.setGlowColorOverride(glow);
        }
        if (background != null) {
            display.setBackgroundColor(background);
        }
        if (textOpacity != null) {
            display.setTextOpacity(textOpacity);
        }
        if (lineWidth != null) {
            display.setLineWidth(lineWidth);
        }
        if (viewRange != null) {
            display.setViewRange(viewRange);
        }
        if (brightness != null) {
            display.setBrightness(brightness);
        }
    }
}

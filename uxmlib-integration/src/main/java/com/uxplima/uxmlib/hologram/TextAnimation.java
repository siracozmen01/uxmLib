package com.uxplima.uxmlib.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

/**
 * Pure builders for hologram animation frame lists — no server, no entity, so they are unit-testable.
 * Hand the resulting frames to {@link HologramAnimation#animate} to play them. A frame is one
 * {@link Component} the hologram shows for one tick of the animation's period.
 */
public final class TextAnimation {

    private TextAnimation() {}

    /** Use {@code frames} verbatim as the animation, cycling in order. */
    public static List<Component> frames(List<Component> frames) {
        Objects.requireNonNull(frames, "frames");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        return List.copyOf(frames);
    }

    /**
     * A typewriter reveal of {@code text}: frame {@code i} shows the first {@code i} characters, ending on
     * the full string. Plain text only (no per-character styling).
     */
    public static List<Component> typewriter(String text) {
        Objects.requireNonNull(text, "text");
        List<Component> frames = new ArrayList<>(text.length() + 1);
        for (int i = 1; i <= text.length(); i++) {
            frames.add(Component.text(text.substring(0, i)));
        }
        if (frames.isEmpty()) {
            frames.add(Component.text(""));
        }
        return frames;
    }

    /**
     * A marquee scroll of {@code text} through a window {@code windowWidth} characters wide: each frame
     * slides the visible window one character along, wrapping around with a gap so it loops cleanly.
     */
    public static List<Component> scroll(String text, int windowWidth) {
        Objects.requireNonNull(text, "text");
        if (windowWidth < 1) {
            throw new IllegalArgumentException("windowWidth must be >= 1");
        }
        String padded = text + " ".repeat(windowWidth);
        List<Component> frames = new ArrayList<>(padded.length());
        for (int start = 0; start < padded.length(); start++) {
            frames.add(Component.text(window(padded, start, windowWidth)));
        }
        return frames;
    }

    private static String window(String source, int start, int width) {
        StringBuilder out = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            out.append(source.charAt((start + i) % source.length()));
        }
        return out.toString();
    }
}

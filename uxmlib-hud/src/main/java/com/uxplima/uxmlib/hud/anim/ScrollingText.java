package com.uxplima.uxmlib.hud.anim;

import java.util.Objects;

import net.kyori.adventure.text.Component;

/**
 * A marquee ticker: a fixed-width window slides one character per {@link #advance()} over the source text,
 * wrapping back to the start through an optional separator gap so a long label rolls endlessly across a narrow
 * slot (a sidebar line, a bar title). The window is drawn modulo the ring of {@code text + separator}; if no
 * separator is given and the text already fits the width, the text is returned whole and stepping is a no-op.
 *
 * <p>Pure and Bukkit-free: the frame is a plain text component, so the rolling sequence is asserted directly.
 */
public final class ScrollingText implements TextAnimator {

    private final String ring;
    private final int width;
    private final boolean scrolling;
    private int offset;

    private ScrollingText(String ring, int width, boolean scrolling) {
        this.ring = ring;
        this.width = width;
        this.scrolling = scrolling;
    }

    /** A ticker over {@code text} in a {@code width}-character window with no wrap separator. */
    public static ScrollingText of(String text, int width) {
        return of(text, width, "");
    }

    /**
     * A ticker over {@code text} in a {@code width}-character window. A non-empty {@code separator} is inserted
     * between the end and the start of the text so the marquee always wraps even when the text fits the slot.
     */
    public static ScrollingText of(String text, int width, String separator) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(separator, "separator");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        String ring = text + separator;
        boolean scrolling = separator.isEmpty() ? text.length() > width : true;
        return new ScrollingText(scrolling ? ring : text, width, scrolling);
    }

    @Override
    public Component frame() {
        if (!scrolling) {
            return Component.text(ring);
        }
        StringBuilder window = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            window.append(ring.charAt((offset + i) % ring.length()));
        }
        return Component.text(window.toString());
    }

    @Override
    public void advance() {
        if (scrolling) {
            offset = (offset + 1) % ring.length();
        }
    }

    @Override
    public void reset() {
        offset = 0;
    }
}

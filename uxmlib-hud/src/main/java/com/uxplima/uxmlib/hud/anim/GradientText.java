package com.uxplima.uxmlib.hud.anim;

import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

/**
 * An animated multi-stop gradient: every character of the source carries its own colour interpolated across
 * the stop list, and the whole sweep shifts by one phase step each {@link #advance()}, returning to its start
 * after {@code steps} advances. This is the per-character colour motion MiniMessage cannot produce statically;
 * a caller advances it on the shared tick and feeds {@link #frame()} into a sidebar title or a bar name.
 *
 * <p>Pure and Bukkit-free: the frame is a component whose children are the coloured characters, so the colour
 * sequence across ticks is asserted directly. Colour blending uses Adventure's {@link TextColor#lerp}.
 */
public final class GradientText implements TextAnimator {

    private static final int DEFAULT_STEPS = 20;

    private final String text;
    private final List<TextColor> stops;
    private final int steps;
    private int step;

    private GradientText(String text, List<TextColor> stops, int steps) {
        this.text = text;
        this.stops = stops;
        this.steps = steps;
    }

    /** A gradient over {@code text} across {@code stops}, completing a full sweep over the default step count. */
    public static GradientText of(String text, List<? extends TextColor> stops) {
        return of(text, stops, DEFAULT_STEPS);
    }

    /**
     * A gradient over {@code text} across {@code stops} that returns to its starting frame every {@code steps}
     * advances. At least two stops are required; more steps make the sweep slower and smoother.
     */
    public static GradientText of(String text, List<? extends TextColor> stops, int steps) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(stops, "stops");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (stops.size() < 2) {
            throw new IllegalArgumentException("a gradient needs at least two colour stops");
        }
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be positive");
        }
        return new GradientText(text, List.copyOf(stops), steps);
    }

    @Override
    public Component frame() {
        float phase = (float) step / steps;
        int n = text.length();
        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < n; i++) {
            float base = n == 1 ? 0f : (float) i / (n - 1);
            float t = wrap(base + phase);
            builder.append(Component.text(text.charAt(i)).color(colourAt(t)));
        }
        return builder.build();
    }

    @Override
    public void advance() {
        step = (step + 1) % steps;
    }

    @Override
    public void reset() {
        step = 0;
    }

    /** Map {@code t} in [0,1] onto the multi-stop gradient with a linear blend inside the spanning segment. */
    private TextColor colourAt(float t) {
        int segments = stops.size() - 1;
        float scaled = t * segments;
        int index = Math.min((int) scaled, segments - 1);
        float local = scaled - index;
        return TextColor.lerp(local, stops.get(index), stops.get(index + 1));
    }

    private static float wrap(float value) {
        float fractional = value - (float) Math.floor(value);
        return fractional >= 1f ? 0f : fractional;
    }
}

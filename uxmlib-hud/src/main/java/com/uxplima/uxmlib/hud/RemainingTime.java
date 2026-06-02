package com.uxplima.uxmlib.hud;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.common.Durations;

/**
 * A MiniMessage placeholder for the time a timed HUD surface has left. Given a supplier of the remaining
 * {@link Duration}, it builds a {@link TagResolver} whose tag inserts that value formatted through uxmlib
 * {@link Durations} (so {@code "1m 30s"}, the same vocabulary as cooldowns and bans). A boss-bar or
 * action-bar title can carry the tag — {@code "<red>Ends in <time>"} — and re-render it each tick to show a
 * live countdown.
 *
 * <p>The supplier is read lazily, once per render and only if the tag actually appears in the template, so a
 * title that omits it pays nothing. This is a pure resolver factory: it holds no per-player state and never
 * mutates anything. The default key answers both {@code <time>} and the {@code <auto_time_left>} alias.
 * MiniMessage tag names are lowercase, so the alias uses {@code snake_case} rather than {@code camelCase}.
 */
public final class RemainingTime {

    /** The conventional tag name and its longer alias, both answered by {@link #resolver(Supplier)}. */
    private static final Set<String> DEFAULT_KEYS = Set.of("time", "auto_time_left");

    private RemainingTime() {}

    /**
     * A resolver for {@code <time>} (and the {@code <auto_time_left>} alias) that renders the value of
     * {@code remaining} at each render, formatted via {@link Durations#format(Duration)}.
     */
    public static TagResolver resolver(Supplier<Duration> remaining) {
        Objects.requireNonNull(remaining, "remaining");
        return TagResolver.resolver(DEFAULT_KEYS, (queue, ctx) -> tag(remaining));
    }

    /**
     * A resolver for a single {@code key} that renders the value of {@code remaining} at each render,
     * formatted via {@link Durations#format(Duration)}.
     *
     * @throws IllegalArgumentException if {@code key} is blank
     */
    public static TagResolver resolver(String key, Supplier<Duration> remaining) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(remaining, "remaining");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return TagResolver.resolver(key, (queue, ctx) -> tag(remaining));
    }

    private static Tag tag(Supplier<Duration> remaining) {
        Duration left = Objects.requireNonNull(remaining.get(), "remaining supplied null");
        // A bar that has just lapsed can report a tiny negative remainder; show 0s rather than a stray "ms".
        Duration clamped = left.isNegative() ? Duration.ZERO : left;
        return Tag.inserting(Component.text(Durations.format(clamped)));
    }
}

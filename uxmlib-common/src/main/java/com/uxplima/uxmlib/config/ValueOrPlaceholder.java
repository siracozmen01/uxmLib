package com.uxplima.uxmlib.config;

import java.util.Objects;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * A config field that is <em>either</em> a fixed literal value <em>or</em> a placeholder template resolved
 * later. This lets an admin write {@code "Steve"} or {@code "%player_name%"} in one slot: the library keeps
 * the choice as data and only resolves the placeholder — through a caller-supplied resolver — at the moment
 * the value is needed.
 *
 * <p>The resolver is a plain {@code Function<String, T>}, a functional seam rather than a hard dependency on
 * any placeholder engine: a consumer passes a PlaceholderAPI-backed function, a fake one in tests, or any
 * lookup it likes. A {@link #literal(Object) literal} ignores the resolver entirely; a
 * {@link #placeholder(String) placeholder} calls it on every {@link #resolve(Function)} so live values stay
 * fresh (resolution is lazy, never cached).
 *
 * @param <T> the resolved value type
 */
public final class ValueOrPlaceholder<T> {

    private final @Nullable T value;
    private final @Nullable String template;

    private ValueOrPlaceholder(@Nullable T value, @Nullable String template) {
        this.value = value;
        this.template = template;
    }

    /** A fixed value that resolves to itself, ignoring any resolver. */
    public static <T> ValueOrPlaceholder<T> literal(T value) {
        Objects.requireNonNull(value, "value");
        return new ValueOrPlaceholder<>(value, null);
    }

    /** A placeholder {@code template} (e.g. {@code "%player_name%"}) resolved lazily through the resolver. */
    public static <T> ValueOrPlaceholder<T> placeholder(String template) {
        Objects.requireNonNull(template, "template");
        if (template.isBlank()) {
            throw new IllegalArgumentException("placeholder template must not be blank");
        }
        return new ValueOrPlaceholder<>(null, template);
    }

    /** Whether this carries a placeholder template (rather than a literal). */
    public boolean isPlaceholder() {
        return template != null;
    }

    /** The placeholder template, or empty for a literal. */
    public String template() {
        return template == null ? "" : template;
    }

    /**
     * Resolve to a concrete value: a literal returns itself; a placeholder applies {@code resolver} to its
     * template. The resolver is invoked on every call (no caching), so a placeholder reflects the latest
     * state each time.
     */
    public T resolve(Function<String, T> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        if (template != null) {
            return Objects.requireNonNull(
                    resolver.apply(template), "resolver returned null for placeholder: " + template);
        }
        return Objects.requireNonNull(value, "value");
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ValueOrPlaceholder<?> that
                && Objects.equals(value, that.value)
                && Objects.equals(template, that.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, template);
    }

    @Override
    public String toString() {
        return template != null ? "placeholder(" + template + ")" : "literal(" + value + ")";
    }
}

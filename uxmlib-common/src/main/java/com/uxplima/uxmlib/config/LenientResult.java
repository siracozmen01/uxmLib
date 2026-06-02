package com.uxplima.uxmlib.config;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of a lenient read (see {@link HoconConfig#getListLenient} /
 * {@link HoconConfig#getSectionLenient}): the entries that parsed cleanly, plus a {@link ConfigViolation}
 * for every entry that was skipped. One bad entry never aborts the whole read, so {@link #value()} always
 * holds the usable rows and {@link #skipped()} explains what was dropped.
 *
 * @param <T> the collection type read (a {@code List<E>} or {@code Map<String, E>})
 */
public record LenientResult<T>(T value, List<ConfigViolation> skipped) {

    public LenientResult {
        Objects.requireNonNull(value, "value");
        skipped = List.copyOf(skipped);
    }

    /** Whether every entry parsed (nothing was skipped). */
    public boolean allParsed() {
        return skipped.isEmpty();
    }
}

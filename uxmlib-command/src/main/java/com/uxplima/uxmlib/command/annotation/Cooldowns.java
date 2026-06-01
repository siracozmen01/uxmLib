package com.uxplima.uxmlib.command.annotation;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A dependency-free, thread-safe cooldown gate keyed by an opaque string (the command path plus the
 * sender's UUID, in practice). Each key maps to the wall-clock millis at which its cooldown expires;
 * {@link #check} reports the time still to wait and re-arms when the window has elapsed. There is no
 * background task: expired entries are evicted lazily as they are checked, the model mythiclib's
 * {@code CooldownMap} uses. The clock is injectable so the arm/expire progression is unit-testable
 * without sleeping; the default is wall time.
 *
 * <p>This is a plain instance with no static mutable state — construct one and share it across a set of
 * registrations through {@link ParamResolvers#cooldowns(Cooldowns)} so they all see the same windows.
 */
public final class Cooldowns {

    private final ConcurrentHashMap<String, Long> expiryByKey = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    /** A cooldown store driven by wall-clock time ({@link System#currentTimeMillis()}). */
    public Cooldowns() {
        this(System::currentTimeMillis);
    }

    /**
     * A cooldown store driven by {@code clock}, a supplier of "now" in epoch millis. Pass a controllable
     * supplier in tests; production code wants the no-arg constructor.
     */
    public Cooldowns(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Report and refresh the cooldown for {@code key}. If the key is still cooling down, returns the
     * positive millis left to wait and leaves the window untouched. Otherwise arms a fresh window of
     * {@code durationMillis} and returns {@code 0}. A non-positive {@code durationMillis} is a no-op that
     * always returns {@code 0} (and stores nothing).
     *
     * @param key an opaque, stable identity for the gated action; never {@code null}
     * @param durationMillis how long a freshly armed window lasts, in millis
     * @return the remaining millis to wait, or {@code 0} when the action may proceed (and was re-armed)
     */
    public long check(String key, long durationMillis) {
        Objects.requireNonNull(key, "key");
        if (durationMillis <= 0L) {
            return 0L;
        }
        long now = clock.getAsLong();
        Long expiry = expiryByKey.get(key);
        if (expiry != null && expiry > now) {
            return expiry - now;
        }
        expiryByKey.put(key, now + durationMillis);
        return 0L;
    }

    /** The number of live (or not-yet-evicted) entries. Exposed for tests and diagnostics. */
    public int size() {
        return expiryByKey.size();
    }
}

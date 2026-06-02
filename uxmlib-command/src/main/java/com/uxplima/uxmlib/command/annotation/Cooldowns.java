package com.uxplima.uxmlib.command.annotation;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

/**
 * A dependency-free, thread-safe cooldown gate keyed by an opaque string (the command path plus the
 * sender's UUID, in practice). Each key maps to the wall-clock millis at which its cooldown expires;
 * {@link #check} reports the time still to wait and re-arms when the window has elapsed. There is no
 * background task: expired entries are evicted lazily as they are checked, the model mythiclib's
 * {@code CooldownMap} uses. The clock is injectable so the arm/expire progression is unit-testable
 * without sleeping; the default is wall time.
 *
 * <p>By default windows live only in memory and are lost on restart. To make long cooldowns (a daily kit,
 * a weekly reward) survive one, back the gate with a {@link CooldownStore} via
 * {@link #Cooldowns(LongSupplier, CooldownStore)}: a cold key is read through the store, and arming a window
 * writes through it. The store is an optional seam so this module never depends on storage.
 *
 * <p>This is a plain instance with no static mutable state — construct one and share it across a set of
 * registrations through {@link ParamResolvers#cooldowns(Cooldowns)} so they all see the same windows.
 */
public final class Cooldowns {

    private final ConcurrentHashMap<String, Long> expiryByKey = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final @Nullable CooldownStore store;

    /** A cooldown store driven by wall-clock time ({@link System#currentTimeMillis()}). */
    public Cooldowns() {
        this(System::currentTimeMillis, null);
    }

    /**
     * A cooldown store driven by {@code clock}, a supplier of "now" in epoch millis. Pass a controllable
     * supplier in tests; production code wants the no-arg constructor.
     */
    public Cooldowns(LongSupplier clock) {
        this(clock, null);
    }

    /**
     * A cooldown gate driven by {@code clock} and persisted through {@code store}, so windows armed before a
     * restart are recovered the first time their key is checked again. Pass {@code null} for {@code store} to
     * keep the windows in memory only.
     */
    public Cooldowns(LongSupplier clock, @Nullable CooldownStore store) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.store = store;
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
        warmFromStore(key);
        long newExpiry = now + durationMillis;
        boolean[] armed = {false};
        // compute() runs the check-and-arm under the per-key bin lock the map already holds, so two threads
        // racing the same elapsed key cannot both observe expiry<=now and both arm a fresh window.
        long resolved = expiryByKey.compute(key, (ignored, current) -> {
            if (current != null && current > now) {
                return current;
            }
            armed[0] = true;
            return newExpiry;
        });
        if (!armed[0]) {
            return resolved - now;
        }
        // We won the arm: persist outside the bin lock so a store write never blocks other keys' checks.
        if (store != null) {
            store.save(key, newExpiry);
        }
        return 0L;
    }

    /**
     * Pull a cold key's persisted expiry into memory once, so the atomic check-and-arm in {@link #check} sees
     * it. A no-op when the key is already in memory or no {@link CooldownStore} backs this gate.
     */
    private void warmFromStore(String key) {
        if (store == null || expiryByKey.containsKey(key)) {
            return;
        }
        OptionalLong persisted = store.load(key);
        if (persisted.isPresent()) {
            expiryByKey.putIfAbsent(key, persisted.getAsLong());
        }
    }

    /** The number of live (or not-yet-evicted) entries. Exposed for tests and diagnostics. */
    public int size() {
        return expiryByKey.size();
    }
}

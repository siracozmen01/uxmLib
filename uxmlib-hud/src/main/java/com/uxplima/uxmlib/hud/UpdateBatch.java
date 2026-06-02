package com.uxplima.uxmlib.hud;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Coalesces rapid UI updates so that many {@code mark} calls for the same key within one tick become a single
 * render. The first {@code mark} after an idle period arms one {@link Scheduler#globalLater} flush a tick out;
 * further marks before it fires just add to the dirty set. When the flush runs it renders each dirty key once,
 * in first-marked order, through the caller-supplied renderer.
 *
 * <p>A re-entrancy guard makes the flush ignore marks that originate from inside a render — a renderer that
 * touches the same batch cannot re-dirty its own key or arm a second flush, so there is no feedback loop. The
 * key type is whatever the owner uses to identify a surface (a player UUID, a sidebar handle, an enum).
 *
 * <p>Additive helper: managers may route their per-player updates through one of these, but the existing
 * direct-update paths are untouched. Drive it from a single scheduler thread; it is not thread-safe.
 *
 * @param <K> the identity of an updatable surface
 */
public final class UpdateBatch<K> {

    private static final Duration ONE_TICK = Duration.ofMillis(50L);

    private final Scheduler scheduler;
    private final Consumer<K> renderer;
    private final Set<K> dirty = new LinkedHashSet<>();
    private boolean armed;
    private boolean flushing;

    public UpdateBatch(Scheduler scheduler, Consumer<K> renderer) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * Mark {@code key} dirty so it renders on the next flush. Marks made from inside a flush are skipped (the
     * re-entrancy guard), so a render cannot feed back into the flush that triggered it.
     */
    public void mark(K key) {
        Objects.requireNonNull(key, "key");
        if (flushing) {
            return;
        }
        dirty.add(key);
        arm();
    }

    /** Whether a flush is currently scheduled but not yet run. Exposed for owners that want to inspect state. */
    public boolean isArmed() {
        return armed;
    }

    private void arm() {
        if (armed) {
            return;
        }
        armed = true;
        scheduler.globalLater(ONE_TICK, this::flush);
    }

    private void flush() {
        armed = false;
        if (dirty.isEmpty()) {
            return;
        }
        Set<K> batch = new LinkedHashSet<>(dirty);
        dirty.clear();
        flushing = true;
        try {
            for (K key : batch) {
                renderer.accept(key);
            }
        } finally {
            flushing = false;
        }
    }
}

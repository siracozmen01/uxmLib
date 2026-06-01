package com.uxplima.uxmlib.hologram;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * Plays a frame list onto a {@link Hologram} on a timer, updating its text in place (no re-spawn, no
 * packets). The loop runs on the entity's region thread through the injected {@link Scheduler}, so it is
 * Folia-safe and self-cancels if the entity is removed. Build frames with {@link TextAnimation}.
 */
public final class HologramAnimation {

    private final Scheduler scheduler;

    public HologramAnimation(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Cycle {@code frames} on {@code hologram}, advancing every {@code period}. Returns a {@link TaskHandle}
     * to stop it; the task also stops on its own if the hologram entity is removed.
     */
    public TaskHandle animate(Hologram hologram, List<Component> frames, Duration period) {
        Objects.requireNonNull(hologram, "hologram");
        Objects.requireNonNull(period, "period");
        List<Component> sequence = List.copyOf(frames);
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        int[] index = {0};
        return scheduler.entityTimer(hologram.entity(), Duration.ZERO, period, handle -> {
            hologram.setText(sequence.get(index[0] % sequence.size()));
            index[0]++;
        });
    }
}

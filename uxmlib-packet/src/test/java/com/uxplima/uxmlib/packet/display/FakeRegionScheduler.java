package com.uxplima.uxmlib.packet.display;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * A Scheduler that captures the one-shot first frame and the repeating refresh the hologram starts, so a test
 * can drive a frame with {@link #tick()} and see that the handle was cancelled. Every family the hologram must
 * not use throws, so reaching for the wrong thread is caught here rather than on a live server.
 */
final class FakeRegionScheduler implements Scheduler {

    private @Nullable Runnable firstFrame;
    private @Nullable Consumer<TaskHandle> timerTask;
    private @Nullable Location timerLocation;
    private boolean cancelled;

    private final TaskHandle handle = new TaskHandle() {
        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    };

    @Override
    public TaskHandle region(Location location, Runnable task) {
        this.firstFrame = task;
        return handle;
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        this.timerLocation = location;
        this.timerTask = task;
        this.cancelled = false;
        return handle;
    }

    /** Run the first frame the hologram scheduled onto the anchor's region. */
    void firstFrame() {
        Runnable task = firstFrame;
        if (task != null) {
            task.run();
        }
    }

    /** Fire the captured refresh once, as a tick would. */
    void tick() {
        Consumer<TaskHandle> task = timerTask;
        if (task != null) {
            task.accept(handle);
        }
    }

    boolean cancelled() {
        return cancelled;
    }

    @Nullable Location timerLocation() {
        return timerLocation;
    }

    boolean hasTimer() {
        return timerTask != null;
    }

    // Unused Scheduler members for these tests.
    @Override
    public TaskHandle global(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle async(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException();
    }
}

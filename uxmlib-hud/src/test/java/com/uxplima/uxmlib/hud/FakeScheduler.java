package com.uxplima.uxmlib.hud;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/** A Scheduler that captures the single global timer so a test can fire it and observe cancellation. */
public final class FakeScheduler implements Scheduler {

    private @Nullable Consumer<TaskHandle> timerTask;
    private @Nullable Runnable laterTask;
    private boolean cancelled;
    private int starts;
    private int laters;

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
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        this.timerTask = task;
        this.cancelled = false;
        this.starts++;
        return handle;
    }

    public void fire() {
        Consumer<TaskHandle> task = timerTask;
        if (task != null) {
            task.accept(handle);
        }
    }

    /** Run the most recently scheduled one-shot {@code globalLater} task, clearing it like a real tick would. */
    public void runLater() {
        Runnable task = laterTask;
        laterTask = null;
        if (task != null) {
            task.run();
        }
    }

    public boolean cancelled() {
        return cancelled;
    }

    public int starts() {
        return starts;
    }

    public int laters() {
        return laters;
    }

    // Unused Scheduler members for these tests.
    @Override
    public TaskHandle global(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        this.laterTask = task;
        this.laters++;
        return handle;
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
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

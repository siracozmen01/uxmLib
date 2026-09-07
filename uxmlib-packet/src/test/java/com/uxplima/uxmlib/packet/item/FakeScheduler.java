package com.uxplima.uxmlib.packet.item;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * A Scheduler that records the delayed reorder the installer asks for, so a test can see that a join
 * scheduled one and against which player. Every other family throws: reaching for the wrong thread is caught
 * here rather than on a live server.
 */
final class FakeScheduler implements Scheduler {

    record Delayed(Entity entity, Duration delay, Runnable task) {}

    private final List<Delayed> delayed = new ArrayList<>();

    private static final TaskHandle HANDLE = new TaskHandle() {
        @Override
        public void cancel() {
            // nothing to cancel: the fake runs nothing on its own.
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        delayed.add(new Delayed(entity, delay, task));
        return HANDLE;
    }

    List<Delayed> delayed() {
        return List.copyOf(delayed);
    }

    /** Run every delayed task the installer scheduled, as the server would once the delay is up. */
    void runDelayed() {
        for (Delayed each : List.copyOf(delayed)) {
            each.task().run();
        }
    }

    @Override
    public TaskHandle global(Runnable task) {
        throw unexpected();
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        throw unexpected();
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw unexpected();
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        throw unexpected();
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        throw unexpected();
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw unexpected();
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        throw unexpected();
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw unexpected();
    }

    @Override
    public TaskHandle async(Runnable task) {
        throw unexpected();
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        throw unexpected();
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw unexpected();
    }

    private static AssertionError unexpected() {
        return new AssertionError("the item view installer asked for a thread it must not use");
    }
}

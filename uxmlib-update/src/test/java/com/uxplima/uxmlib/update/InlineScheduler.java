package com.uxplima.uxmlib.update;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * A test {@link Scheduler} that runs async work inline so a checker's futures resolve synchronously in the test
 * thread. Only the async family is used by the update checker; the region/entity families throw if touched, so
 * a test that accidentally relies on them fails loudly rather than silently no-op'ing.
 */
final class InlineScheduler implements Scheduler {

    private static TaskHandle noHandle() {
        return new TaskHandle() {
            @Override
            public void cancel() {}

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    @Override
    public TaskHandle async(Runnable task) {
        task.run();
        return noHandle();
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        task.run();
        return noHandle();
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        task.accept(noHandle());
        return noHandle();
    }

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
}

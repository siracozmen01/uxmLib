package com.uxplima.uxmlib.menu.property;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * A scheduler that runs everything inline and counts the hops, so a property's write-then-redraw order is observable
 * without a server. Only the two methods the editable properties use are implemented; anything else throws rather
 * than quietly running on the wrong thread, so a property that starts using a third hop fails here instead of
 * passing for the wrong reason.
 */
final class SameThreadScheduler implements Scheduler {

    private static final TaskHandle FINISHED = new TaskHandle() {

        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    int asyncHops;

    int entityHops;

    @Override
    public TaskHandle async(Runnable task) {
        asyncHops++;
        task.run();
        return FINISHED;
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        entityHops++;
        task.run();
        return FINISHED;
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
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
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

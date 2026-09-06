package com.uxplima.uxmlib.menu.support;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * A scheduler that runs everything inline and counts the hops, so a write-then-redraw order is observable without a
 * server. Only the two methods the editable properties and the menu facade use are implemented; anything else throws
 * rather than quietly running on the wrong thread, so code that starts taking a third hop fails here instead of
 * passing for the wrong reason.
 *
 * <p>Shared rather than copied per package. A caller that legitimately needs a hop this refuses subclasses it and
 * overrides that one method, so the refusal stays the default and the exception is named where it is taken.
 */
public class SameThreadScheduler implements Scheduler {

    /** The handle every implemented hop hands back: the work is already done by the time the caller sees it. */
    protected static final TaskHandle FINISHED = new TaskHandle() {

        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    public int asyncHops;

    public int entityHops;

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
        throw new UnsupportedOperationException("a global hop is not one the editable properties take");
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        throw new UnsupportedOperationException("a delayed global hop is not one the editable properties take");
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a repeating global hop is not one the editable properties take");
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        throw new UnsupportedOperationException("a region hop is not one the editable properties take");
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        throw new UnsupportedOperationException("a delayed region hop is not one the editable properties take");
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a repeating region hop is not one the editable properties take");
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        throw new UnsupportedOperationException("a delayed entity hop is not one the editable properties take");
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a repeating entity hop is not one the editable properties take");
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        throw new UnsupportedOperationException("a delayed async hop is not one the editable properties take");
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        throw new UnsupportedOperationException("a repeating async hop is not one the editable properties take");
    }
}

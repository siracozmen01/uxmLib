package com.uxplima.uxmlib.command.annotation;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * A {@link Scheduler} that runs every task immediately on the calling thread. It is the fallback the
 * server-less {@link AnnotatedCommands#buildNode(Object)} entry points use when no real {@link Scheduler}
 * was threaded in: those paths only build and inspect the node tree, they never dispatch, so an async
 * handler's continuation never actually runs through this. The real registration path supplies a
 * {@code PaperScheduler}. Timer/delay variants run the task once and hand back a finished handle, which is
 * all the build/inspection paths need.
 */
// Non-final so a test double can extend it to record which family routed a continuation; all overridable
// methods are simple inline runners with no invariant to protect.
class SameThreadScheduler implements Scheduler {

    @Override
    public TaskHandle global(Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return runTimer(task);
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return runTimer(task);
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        return runTimer(task);
    }

    @Override
    public TaskHandle async(Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        return run(task);
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        return runTimer(task);
    }

    private static TaskHandle run(Runnable task) {
        task.run();
        return FINISHED;
    }

    private static TaskHandle runTimer(Consumer<TaskHandle> task) {
        task.accept(FINISHED);
        return FINISHED;
    }

    private static final TaskHandle FINISHED = new TaskHandle() {
        @Override
        public void cancel() {}

        @Override
        public boolean isCancelled() {
            return true;
        }
    };
}

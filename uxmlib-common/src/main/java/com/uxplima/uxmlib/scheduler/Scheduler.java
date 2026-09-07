package com.uxplima.uxmlib.scheduler;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Folia-ready task scheduling. Each family of methods targets one of Paper's schedulers:
 *
 * <ul>
 *   <li><b>global</b>: the global region (world-wide state: time, weather).</li>
 *   <li><b>region</b>: the region owning a {@link Location} (block and world edits there).</li>
 *   <li><b>entity</b>: the region currently owning an {@link Entity} (follows it across region hops);
 *       the task is silently dropped if the entity has been removed.</li>
 *   <li><b>async</b>: off the main threads entirely (I/O, network); never touch the Bukkit API here.</li>
 * </ul>
 *
 * <p>Every method returns a {@link TaskHandle} for cancellation. Timer variants hand the task its own
 * handle so a repeating task can stop itself. Delays and periods are {@link Duration}s, rounded to whole
 * ticks for the tick-based schedulers and to milliseconds for the async one.
 *
 * <p>Teardown is the implementation's to settle, and {@link PaperScheduler} documents how it does: a
 * server refuses to schedule anything for a plugin that is already disabled, so a one-shot handed to a
 * tick-based family during {@code onDisable} runs inline on the thread that owns it, while a timer and any
 * off-thread work are dropped with a warning. Teardown that must happen off-thread has to happen before the
 * plugin disables.
 */
public interface Scheduler {

    /** Run on the global region as soon as possible. */
    TaskHandle global(Runnable task);

    /** Run on the global region after {@code delay}. */
    TaskHandle globalLater(Duration delay, Runnable task);

    /** Run repeatedly on the global region after {@code delay}, every {@code period}. */
    TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task);

    /** Run on the region owning {@code location} as soon as possible. */
    TaskHandle region(Location location, Runnable task);

    /** Run on the region owning {@code location} after {@code delay}. */
    TaskHandle regionLater(Location location, Duration delay, Runnable task);

    /** Run repeatedly on the region owning {@code location} after {@code delay}, every {@code period}. */
    TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task);

    /** Run on the region owning {@code entity} as soon as possible. Dropped if the entity is gone. */
    TaskHandle entity(Entity entity, Runnable task);

    /** Run on the region owning {@code entity} after {@code delay}. Dropped if the entity is gone. */
    TaskHandle entityLater(Entity entity, Duration delay, Runnable task);

    /** Run repeatedly on the region owning {@code entity} after {@code delay}, every {@code period}. */
    TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task);

    /** Run off-thread as soon as possible. The Bukkit API is off-limits here. */
    TaskHandle async(Runnable task);

    /** Run off-thread after {@code delay}. */
    TaskHandle asyncLater(Duration delay, Runnable task);

    /** Run off-thread repeatedly after {@code delay}, every {@code period}. */
    TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task);
}

package com.uxplima.uxmlib.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * The {@link Scheduler} implementation over Paper's native schedulers. The same code runs on Paper and
 * Folia: {@code GlobalRegionScheduler}, {@code RegionScheduler}, the per-entity scheduler and
 * {@code AsyncScheduler} all exist on both, so no version detection is needed.
 *
 * <h2>Teardown</h2>
 *
 * <p>Bukkit refuses to register a task for a plugin that is no longer enabled: it throws
 * {@code IllegalPluginAccessException}. Every teardown path that handed its work to a scheduler therefore
 * threw during {@code onDisable} and the work never ran. That was a live defect, not a theory. It threw twice
 * on Paper 26.2 and five times on Folia 26.2, out of the same two paths: the nametag registry did not hand
 * the scoreboard teams back and the menu engine did not close its windows, so a reload left a player wearing
 * a name no plugin owned and a menu open with no engine behind it.
 *
 * <p>So once {@code plugin.isEnabled()} is false, this class stops scheduling and settles the work itself.
 * A one-shot task runs inline when the calling thread is the one the task asked for, which during
 * {@code onDisable} it is on both platforms. That decision lives here rather than at every call site, so a
 * caller still says which thread it wants and gets it.
 *
 * <p>Two kinds of work cannot be settled that way, and this class refuses them out loud with a warning
 * naming the plugin rather than pretending:
 *
 * <ul>
 *   <li><b>Off the owning thread.</b> On Folia the global region and each world region tick on their own
 *       threads, and running inline from the wrong one would touch that region's state from a thread that does
 *       not own it, which is the corruption the region model exists to prevent. So no family assumes: each asks
 *       the server which thread this is ({@code isOwnedByCurrentRegion} for a location or an entity, and
 *       {@code ownsTheGlobalRegion} for the global region) and refuses when the answer is no. On Paper the
 *       answer is yes for everything on the main thread, so teardown there simply works.</li>
 *   <li><b>Repeating and off-thread work.</b> A timer cannot outlive the plugin that owns it, and async work
 *       cannot be run inline: the async families exist precisely so the Bukkit API is never touched from them,
 *       and running that code on a server thread mid-shutdown would be worse than not running it. Teardown
 *       that must happen off-thread has to happen before the plugin disables, on a path the plugin controls
 *       (a synchronous flush of what the timer was flushing, for example), not here.</li>
 * </ul>
 */
public final class PaperScheduler implements Scheduler {

    private static final String GLOBAL = "the global region";
    private static final String REGION = "the region that owns the location";
    private static final String ENTITY = "the region that owns the entity";

    // The entity scheduler takes a "retired" callback that runs if the entity is removed before the task
    // fires. The library has nothing to do in that case, so each call site passes an inline no-op.
    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public TaskHandle global(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return settle(task, ownsTheGlobalRegion(), GLOBAL);
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run()));
    }

    @Override
    public TaskHandle globalLater(Duration delay, Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            // The tick this delay waits for never arrives, so "later" during teardown means now or never.
            return settle(task, ownsTheGlobalRegion(), GLOBAL);
        }
        return new PaperTaskHandle(
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Ticks.fromDuration(delay)));
    }

    @Override
    public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return refuseRepeating(GLOBAL);
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(
                        plugin,
                        t -> task.accept(new PaperTaskHandle(t)),
                        Ticks.fromDuration(delay),
                        Ticks.fromDuration(period)));
    }

    @Override
    public TaskHandle region(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return settle(task, Bukkit.isOwnedByCurrentRegion(location), REGION);
        }
        return new PaperTaskHandle(Bukkit.getRegionScheduler().run(plugin, location, t -> task.run()));
    }

    @Override
    public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return settle(task, Bukkit.isOwnedByCurrentRegion(location), REGION);
        }
        return new PaperTaskHandle(
                Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), Ticks.fromDuration(delay)));
    }

    @Override
    public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return refuseRepeating(REGION);
        }
        return new PaperTaskHandle(Bukkit.getRegionScheduler()
                .runAtFixedRate(
                        plugin,
                        location,
                        t -> task.accept(new PaperTaskHandle(t)),
                        Ticks.fromDuration(delay),
                        Ticks.fromDuration(period)));
    }

    @Override
    public TaskHandle entity(Entity entity, Runnable task) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return settle(task, Bukkit.isOwnedByCurrentRegion(entity), ENTITY);
        }
        return new PaperTaskHandle(entity.getScheduler().run(plugin, t -> task.run(), () -> {}));
    }

    @Override
    public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return settle(task, Bukkit.isOwnedByCurrentRegion(entity), ENTITY);
        }
        return new PaperTaskHandle(
                entity.getScheduler().runDelayed(plugin, t -> task.run(), () -> {}, Ticks.fromDuration(delay)));
    }

    @Override
    public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return refuseRepeating(ENTITY);
        }
        return new PaperTaskHandle(entity.getScheduler()
                .runAtFixedRate(
                        plugin,
                        t -> task.accept(new PaperTaskHandle(t)),
                        () -> {},
                        Ticks.fromDuration(delay),
                        Ticks.fromDuration(period)));
    }

    @Override
    public TaskHandle async(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return refuseOffThread();
        }
        return new PaperTaskHandle(Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run()));
    }

    @Override
    public TaskHandle asyncLater(Duration delay, Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return refuseOffThread();
        }
        return new PaperTaskHandle(
                Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), millis(delay), TimeUnit.MILLISECONDS));
    }

    @Override
    public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
        Objects.requireNonNull(task, "task");
        if (!plugin.isEnabled()) {
            return refuseOffThread();
        }
        return new PaperTaskHandle(Bukkit.getAsyncScheduler()
                .runAtFixedRate(
                        plugin,
                        t -> task.accept(new PaperTaskHandle(t)),
                        millis(delay),
                        millis(period),
                        TimeUnit.MILLISECONDS));
    }

    /**
     * Whether this thread may run global-region work right now, asked of the server rather than assumed.
     *
     * <p>{@code isGlobalTickThread} is the direct answer and on Paper it is the whole answer: it reports the main
     * thread, which is where {@code onDisable} runs. On Folia it is narrower than the truth. Folia stops the server
     * from a dedicated shutdown thread: it halts every region tick and the global tick first, then disables the
     * plugins from that one thread. Nothing is ticking by then, so {@code isGlobalTickThread} is false, while the
     * platform has in fact handed that thread ownership of everything: {@code isOwnedByCurrentRegion} answers true
     * there for every region and every entity, and Folia closes the players' own inventories from it. Refusing
     * there would leave the teams unreturned and the menus open on the one platform that reaches this most.
     *
     * <p>So the second clause names that thread through the API rather than by class: the server is stopping, and
     * this is one of its tick threads. A plugin disabled while the server is still running gets only the first
     * clause, which is what keeps a region tick thread from writing global state on Folia.
     */
    private static boolean ownsTheGlobalRegion() {
        return Bukkit.isGlobalTickThread() || (Bukkit.getServer().isStopping() && Bukkit.isPrimaryThread());
    }

    /**
     * Settle one-shot work for a plugin that is no longer enabled. It runs here and now when this thread is
     * the one the work asked for, and is refused when it is not. A task that throws is reported and contained,
     * exactly as the server would have contained it, so one failing teardown step never aborts the rest of
     * {@code onDisable}.
     */
    private TaskHandle settle(Runnable task, boolean thisThreadOwnsIt, String owner) {
        if (!thisThreadOwnsIt) {
            return refuse("A task for " + owner + " was dropped. " + plugin.getName()
                    + " is disabled, so it cannot be scheduled, and this thread does not own that region, "
                    + "so it cannot be run here either.");
        }
        try {
            task.run();
        } catch (RuntimeException failure) {
            plugin.getLogger()
                    .log(Level.SEVERE, "A task run during " + plugin.getName() + "'s shutdown threw.", failure);
        }
        return SettledTaskHandle.RAN;
    }

    private TaskHandle refuseRepeating(String owner) {
        return refuse("A repeating task for " + owner + " was dropped. " + plugin.getName()
                + " is disabled, and a timer cannot outlive the plugin that owns it.");
    }

    private TaskHandle refuseOffThread() {
        return refuse("An off-thread task was dropped. " + plugin.getName()
                + " is disabled, so it cannot be scheduled, and off-thread work must not be run on a server "
                + "thread instead. Work like this belongs before the plugin disables, not during it.");
    }

    private TaskHandle refuse(String reason) {
        plugin.getLogger().warning(reason);
        return SettledTaskHandle.REFUSED;
    }

    private static long millis(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        return Math.max(1L, duration.toMillis());
    }
}

package com.uxplima.uxmlib.update;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * Wires a notify-only update check into a running plugin: it polls an {@link UpdateProvider} on a recurring
 * {@link Scheduler#asyncTimer} (off-thread, never a server thread), logs the result to the console via the
 * {@code checkAndAnnounce} dedupe (once per distinct newer release, not once per process), and registers a
 * permission-gated clickable on-join notice. It never self-downloads — v1 is notify only.
 *
 * <p>This is the module's public entry point. Construct it with the running plugin, the library scheduler, and
 * a configured {@link UpdateChecker}, then call {@link #start(Duration, Duration)} once during enable and
 * {@link #stop()} on disable. Both are idempotent and {@code start} can be called again after a {@code stop}.
 */
public final class UpdateNotifier {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final UpdateChecker checker;
    private final String pluginName;
    private final String permission;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private @Nullable TaskHandle pollTask;
    private @Nullable UpdateJoinListener listener;

    /**
     * @param plugin the running plugin (its logger and event registration are used)
     * @param scheduler the library scheduler the poll runs on
     * @param checker the configured checker (provider + current version)
     * @param permission the node a player must hold to see the on-join notice (not {@code isOp()})
     */
    public UpdateNotifier(Plugin plugin, Scheduler scheduler, UpdateChecker checker, String permission) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.checker = Objects.requireNonNull(checker, "checker");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.pluginName = plugin.getName();
    }

    /**
     * Begin polling: an immediate check plus a repeat every {@code period}, and the on-join notice. The console
     * announcement fires the first time an outdated build is seen and again whenever a strictly newer release
     * appears. Idempotent: a second call while already started is a no-op (it does not register a duplicate
     * listener or leak a second timer). Pair with {@link #stop()} on disable.
     *
     * @param initialDelay how long to wait before the first poll
     * @param period the gap between polls
     */
    public void start(Duration initialDelay, Duration period) {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(period, "period");
        if (!started.compareAndSet(false, true)) {
            return;
        }
        UpdateJoinListener joinListener = new UpdateJoinListener(checker, pluginName, permission);
        listener = joinListener;
        plugin.getServer().getPluginManager().registerEvents(joinListener, plugin);
        pollTask = scheduler.asyncTimer(initialDelay, period, handle -> poll());
    }

    /**
     * Cancel the recurring poll and unregister the on-join notice. Safe to call when never started or already
     * stopped; after it returns, {@link #start} may be called again. Call this on plugin disable.
     */
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        TaskHandle task = pollTask;
        if (task != null) {
            task.cancel();
            pollTask = null;
        }
        UpdateJoinListener registered = listener;
        if (registered != null) {
            HandlerList.unregisterAll(registered);
            listener = null;
        }
    }

    private void poll() {
        var ignored = checker.checkAndAnnounce(this::logToConsole);
    }

    private void logToConsole(UpdateOutcome outcome) {
        outcome.release().ifPresent(release -> plugin.getLogger()
                .info(Text.plain(UpdateMessages.notification(
                        pluginName, checker.currentVersion().toString(), release))));
    }
}

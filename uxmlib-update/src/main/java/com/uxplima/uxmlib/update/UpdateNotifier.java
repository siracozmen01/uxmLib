package com.uxplima.uxmlib.update;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;

/**
 * Wires a notify-only update check into a running plugin: it polls an {@link UpdateProvider} on a recurring
 * {@link Scheduler#asyncTimer} (off-thread, never a server thread), logs the result to the console once via the
 * {@code checkAndAnnounce} dedupe, and registers a permission-gated clickable on-join notice. It never
 * self-downloads — v1 is notify only.
 *
 * <p>This is the module's public entry point. Construct it with the running plugin, the library scheduler, and
 * a configured {@link UpdateChecker}, then call {@link #start(Duration, Duration)} once during enable.
 */
public final class UpdateNotifier {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final UpdateChecker checker;
    private final String pluginName;
    private final String permission;

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
     * announcement fires only the first time an outdated build is seen.
     *
     * @param initialDelay how long to wait before the first poll
     * @param period the gap between polls
     */
    public void start(Duration initialDelay, Duration period) {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(period, "period");
        plugin.getServer()
                .getPluginManager()
                .registerEvents(new UpdateJoinListener(checker, pluginName, permission), plugin);
        scheduler.asyncTimer(initialDelay, period, handle -> poll());
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

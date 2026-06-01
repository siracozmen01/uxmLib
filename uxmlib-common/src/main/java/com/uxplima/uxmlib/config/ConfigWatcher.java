package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * Watches a config file for external edits and reloads it. It polls the file's last-modified time through
 * the injected {@link Scheduler} (never a raw {@code WatchService} thread or {@code new Thread}, so it
 * stays Folia-safe) and fires {@code reload} once the modification time has changed and then held steady
 * for one poll — a simple debounce so a half-written save isn't read mid-write.
 */
final class ConfigWatcher {

    private ConfigWatcher() {}

    /** Start polling {@code file} every {@code period}; runs {@code reload} on a settled change. */
    static TaskHandle start(Scheduler scheduler, Path file, Duration period, Runnable reload) {
        long[] lastSeen = {lastModified(file)};
        boolean[] pendingChange = {false};
        return scheduler.asyncTimer(Duration.ZERO, period, handle -> {
            long now = lastModified(file);
            if (now != lastSeen[0]) {
                lastSeen[0] = now;
                pendingChange[0] = true; // changed this tick; wait one more to let a save settle
            } else if (pendingChange[0]) {
                pendingChange[0] = false;
                reload.run();
            }
        });
    }

    private static long lastModified(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }
}

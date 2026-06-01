package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the file-watch hot reload picks up an external edit, debounced, via a fake scheduler. */
class ConfigWatcherTest {

    /** A Scheduler that captures the async timer so the test can fire ticks deterministically. */
    private static final class FakeScheduler implements Scheduler {
        private @org.jspecify.annotations.Nullable Consumer<TaskHandle> timer;
        private boolean cancelled;

        private final TaskHandle handle = new TaskHandle() {
            @Override
            public void cancel() {
                cancelled = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            this.timer = task;
            return handle;
        }

        void tick() {
            if (timer != null) {
                timer.accept(handle);
            }
        }

        // Unused members.
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

        @Override
        public TaskHandle async(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void reloadsOnASettledExternalEdit(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "limit = 1\n");
        HoconConfig config = HoconConfig.load(file);
        assertThat(config.getInt("limit", 0)).isEqualTo(1);

        FakeScheduler scheduler = new FakeScheduler();
        config.watch(scheduler, Duration.ofMillis(50));

        // Edit the file with a new mtime, then tick: first tick sees the change, second tick (settled) reloads.
        Files.writeString(file, "limit = 9\n");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        scheduler.tick(); // change detected, pending
        assertThat(config.getInt("limit", 0)).isEqualTo(1); // not yet — debounced
        scheduler.tick(); // settled -> reload
        assertThat(config.getInt("limit", 0)).isEqualTo(9);
    }

    @Test
    void unwatchStopsTheTimer(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "limit = 1\n");
        HoconConfig config = HoconConfig.load(file);
        FakeScheduler scheduler = new FakeScheduler();
        config.watch(scheduler, Duration.ofMillis(50));

        config.unwatch();
        assertThat(scheduler.cancelled).isTrue();
    }
}

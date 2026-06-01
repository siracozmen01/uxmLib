package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Verifies the shared animation timer starts on the first ticking menu and stops when the last one goes. */
class GuiRegistryTest {

    /** A Scheduler that records the global timer and lets the test fire it and observe cancellation. */
    private static final class FakeScheduler implements Scheduler {
        private @org.jspecify.annotations.Nullable Consumer<TaskHandle> timerTask;
        private boolean cancelled;
        private int starts;

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
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            this.timerTask = task;
            this.cancelled = false;
            this.starts++;
            return handle;
        }

        void fire() {
            Consumer<TaskHandle> task = timerTask;
            if (task != null) {
                task.accept(handle);
            }
        }

        // Unused Scheduler members for this test.
        @Override
        public TaskHandle global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalLater(Duration delay, Runnable task) {
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

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }
    }

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void timerStartsOnFirstRegisterAndStopsOnLastUnregister() {
        FakeScheduler scheduler = new FakeScheduler();
        GuiRegistry registry = new GuiRegistry(scheduler);
        SimpleGui a = Guis.gui().rows(1).build();
        SimpleGui b = Guis.gui().rows(1).build();

        registry.register(a);
        assertThat(scheduler.starts).isEqualTo(1);
        registry.register(b);
        assertThat(scheduler.starts).isEqualTo(1); // still one shared task

        registry.unregister(a);
        assertThat(scheduler.cancelled).isFalse(); // b still ticking
        registry.unregister(b);
        assertThat(scheduler.cancelled).isTrue(); // last one gone
    }

    @Test
    void firingTheTimerTicksRegisteredMenus() {
        FakeScheduler scheduler = new FakeScheduler();
        GuiRegistry registry = new GuiRegistry(scheduler);
        SimpleGui gui = Guis.gui().rows(1).build();
        registry.register(gui);

        long before = gui.ticks();
        scheduler.fire();
        assertThat(gui.ticks()).isEqualTo(before + 1);
    }
}

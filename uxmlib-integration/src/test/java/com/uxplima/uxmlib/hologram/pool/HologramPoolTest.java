package com.uxplima.uxmlib.hologram.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.hologram.Transform;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.Test;

/**
 * Drives the pool's register/tick/diff lifecycle against fakes: a controllable nearby-player supplier and a
 * recording sink stand in for the real entity reads and region-thread show/hide, and a captured
 * global timer lets the test fire one visibility pass at a time. No live world or scheduler is needed.
 */
class HologramPoolTest {

    @Test
    void firstTickShowsToEveryDesiredViewer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        FakeScheduler scheduler = new FakeScheduler();
        RecordingSink sink = new RecordingSink();
        ControllableNearby nearby = new ControllableNearby(Set.of(a, b));
        HologramPool pool = new HologramPool(scheduler, nearby, sink);
        FakeHologram holo = new FakeHologram();

        pool.register(holo, 16.0);
        scheduler.tick();

        assertThat(sink.shown).containsExactlyInAnyOrder(a, b);
        assertThat(sink.hidden).isEmpty();
    }

    @Test
    void onlyTheDeltaIsEmittedAcrossTicks() {
        UUID stays = UUID.randomUUID();
        UUID leaves = UUID.randomUUID();
        UUID joins = UUID.randomUUID();
        FakeScheduler scheduler = new FakeScheduler();
        RecordingSink sink = new RecordingSink();
        ControllableNearby nearby = new ControllableNearby(Set.of(stays, leaves));
        HologramPool pool = new HologramPool(scheduler, nearby, sink);
        FakeHologram holo = new FakeHologram();

        pool.register(holo, 16.0);
        scheduler.tick();
        sink.clear();

        // Second pass: leaves the range, joins enters; stays was already a viewer so it is left untouched.
        nearby.desired = Set.of(stays, joins);
        scheduler.tick();

        assertThat(sink.shown).containsExactly(joins);
        assertThat(sink.hidden).containsExactly(leaves);
    }

    @Test
    void noDeltaWhenTheDesiredSetIsUnchanged() {
        UUID a = UUID.randomUUID();
        FakeScheduler scheduler = new FakeScheduler();
        RecordingSink sink = new RecordingSink();
        HologramPool pool = new HologramPool(scheduler, new ControllableNearby(Set.of(a)), sink);

        pool.register(new FakeHologram(), 16.0);
        scheduler.tick();
        sink.clear();
        scheduler.tick();

        assertThat(sink.shown).isEmpty();
        assertThat(sink.hidden).isEmpty();
    }

    @Test
    void registerStartsTheTaskAndUnregisterStopsItWhenEmpty() {
        FakeScheduler scheduler = new FakeScheduler();
        HologramPool pool = new HologramPool(scheduler, new ControllableNearby(Set.of()), new RecordingSink());
        FakeHologram holo = new FakeHologram();

        assertThat(pool.isRunning()).isFalse();
        pool.register(holo, 16.0);
        assertThat(pool.isRunning()).isTrue();
        assertThat(pool.size()).isEqualTo(1);

        pool.unregister(holo);
        assertThat(pool.size()).isZero();
        assertThat(pool.isRunning()).isFalse();
    }

    @Test
    void unregisterHidesFromEveryCurrentViewer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        FakeScheduler scheduler = new FakeScheduler();
        RecordingSink sink = new RecordingSink();
        HologramPool pool = new HologramPool(scheduler, new ControllableNearby(Set.of(a, b)), sink);
        FakeHologram holo = new FakeHologram();

        pool.register(holo, 16.0);
        scheduler.tick();
        sink.clear();

        pool.unregister(holo);

        assertThat(sink.hidden).containsExactlyInAnyOrder(a, b);
        assertThat(sink.shown).isEmpty();
    }

    @Test
    void rejectsNonPositiveRadius() {
        HologramPool pool =
                new HologramPool(new FakeScheduler(), new ControllableNearby(Set.of()), new RecordingSink());
        assertThatThrownBy(() -> pool.register(new FakeHologram(), 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pool.register(new FakeHologram(), -4.0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A nearby-player source whose answer the test controls between ticks. */
    private static final class ControllableNearby implements NearbyPlayers {
        private Set<UUID> desired;

        ControllableNearby(Set<UUID> desired) {
            this.desired = desired;
        }

        @Override
        public Set<UUID> desiredFor(Hologram hologram, VisibilityGate gate) {
            return desired;
        }
    }

    /** A sink that records the deltas instead of touching Bukkit. */
    private static final class RecordingSink implements ViewerSink {
        private final List<UUID> shown = new ArrayList<>();
        private final List<UUID> hidden = new ArrayList<>();

        @Override
        public void show(Hologram hologram, UUID viewer) {
            shown.add(viewer);
        }

        @Override
        public void hide(Hologram hologram, UUID viewer) {
            hidden.add(viewer);
        }

        void clear() {
            shown.clear();
            hidden.clear();
        }
    }

    /** A scheduler that captures the global timer so one visibility pass can be fired on demand. */
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
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            this.timer = task;
            this.cancelled = false;
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

    /** A Hologram with no Bukkit backing; the pool only touches it through the injected fakes. */
    private static final class FakeHologram implements Hologram {
        @Override
        public void setText(Component text) {}

        @Override
        public void moveTo(Location to, int interpolationTicks) {}

        @Override
        public void setTransform(Transform transform) {}

        @Override
        public boolean attachTo(Entity target) {
            return false;
        }

        @Override
        public void restrictToViewers() {}

        @Override
        public void show(Plugin plugin, Player viewer) {}

        @Override
        public void hide(Plugin plugin, Player viewer) {}

        @Override
        public boolean isVisibleTo(Player viewer) {
            return false;
        }

        @Override
        public void forgetViewer(UUID viewer) {}

        @Override
        public void remove() {}

        @Override
        public TextDisplay entity() {
            throw new UnsupportedOperationException("no entity in the fake");
        }
    }
}

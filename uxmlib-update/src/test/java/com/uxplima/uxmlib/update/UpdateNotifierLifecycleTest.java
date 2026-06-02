package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Asserts {@link UpdateNotifier}'s lifecycle: {@code start} registers exactly one join listener and one poll
 * timer, a second {@code start} does not duplicate either, and {@code stop} cancels the timer and unregisters
 * the listener so no orphaned recurring task or stale handler survives a re-init.
 */
class UpdateNotifierLifecycleTest {

    private static final String PERMISSION = "uxmlib.update.notify";
    private static final Release NEWER = new Release("1.5.0", "https://github.com/o/r/releases/latest");

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // A scheduler that runs async work inline (delegating to InlineScheduler) but hands asyncTimer a real,
    // cancellable handle so the test can observe stop() cancelling it. The region/entity families are unused.
    private static final class RecordingScheduler implements Scheduler {
        private final InlineScheduler delegate = new InlineScheduler();
        private @Nullable RecordingHandle lastTimer;
        private int timerCount;

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            timerCount++;
            RecordingHandle handle = new RecordingHandle();
            lastTimer = handle;
            task.accept(handle);
            return handle;
        }

        boolean lastTimerCancelled() {
            return Objects.requireNonNull(lastTimer, "no timer scheduled").cancelled;
        }

        @Override
        public TaskHandle async(Runnable task) {
            return delegate.async(task);
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            return delegate.asyncLater(delay, task);
        }

        @Override
        public TaskHandle global(Runnable task) {
            return delegate.global(task);
        }

        @Override
        public TaskHandle globalLater(Duration delay, Runnable task) {
            return delegate.globalLater(delay, task);
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            return delegate.globalTimer(delay, period, task);
        }

        @Override
        public TaskHandle region(Location location, Runnable task) {
            return delegate.region(location, task);
        }

        @Override
        public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
            return delegate.regionLater(location, delay, task);
        }

        @Override
        public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
            return delegate.regionTimer(location, delay, period, task);
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            return delegate.entity(entity, task);
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
            return delegate.entityLater(entity, delay, task);
        }

        @Override
        public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
            return delegate.entityTimer(entity, delay, period, task);
        }
    }

    private static final class RecordingHandle implements TaskHandle {
        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private UpdateChecker warmCheckerReporting(Release latest) {
        UpdateProvider provider = () -> CompletableFuture.completedFuture(Optional.of(latest));
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), provider, "1.4.0");
        checker.check().join();
        return checker;
    }

    private int joinHandlerCount() {
        return (int) HandlerList.getRegisteredListeners(plugin).stream()
                .filter(l -> l.getListener() instanceof UpdateJoinListener)
                .count();
    }

    @Test
    void startRegistersExactlyOneListenerAndTimerEvenWhenCalledTwice() {
        RecordingScheduler scheduler = new RecordingScheduler();
        UpdateNotifier notifier = new UpdateNotifier(plugin, scheduler, warmCheckerReporting(NEWER), PERMISSION);

        notifier.start(Duration.ofSeconds(1), Duration.ofMinutes(30));
        notifier.start(Duration.ofSeconds(1), Duration.ofMinutes(30));

        assertThat(scheduler.timerCount).isEqualTo(1);
        assertThat(joinHandlerCount()).isEqualTo(1);
    }

    @Test
    void stopCancelsTheTimerAndUnregistersTheListener() {
        RecordingScheduler scheduler = new RecordingScheduler();
        UpdateNotifier notifier = new UpdateNotifier(plugin, scheduler, warmCheckerReporting(NEWER), PERMISSION);

        notifier.start(Duration.ofSeconds(1), Duration.ofMinutes(30));
        notifier.stop();

        assertThat(scheduler.lastTimerCancelled()).isTrue();
        assertThat(joinHandlerCount()).isZero();
    }

    @Test
    void stopThenStartCleanlyRebindsWithoutLeaking() {
        RecordingScheduler scheduler = new RecordingScheduler();
        UpdateNotifier notifier = new UpdateNotifier(plugin, scheduler, warmCheckerReporting(NEWER), PERMISSION);

        notifier.start(Duration.ofSeconds(1), Duration.ofMinutes(30));
        notifier.stop();
        notifier.start(Duration.ofSeconds(1), Duration.ofMinutes(30));

        assertThat(scheduler.timerCount).isEqualTo(2);
        assertThat(joinHandlerCount()).isEqualTo(1);
    }

    @Test
    void stopIsANoOpWhenNeverStarted() {
        RecordingScheduler scheduler = new RecordingScheduler();
        UpdateNotifier notifier = new UpdateNotifier(plugin, scheduler, warmCheckerReporting(NEWER), PERMISSION);

        notifier.stop();

        assertThat(joinHandlerCount()).isZero();
    }

    @Test
    void afterStopAJoinNoLongerNotifies() {
        RecordingScheduler scheduler = new RecordingScheduler();
        UpdateNotifier notifier = new UpdateNotifier(plugin, scheduler, warmCheckerReporting(NEWER), PERMISSION);

        notifier.start(Duration.ofSeconds(1), Duration.ofMinutes(30));
        notifier.stop();

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, PERMISSION, true);
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));

        assertThat(player.nextComponentMessage()).isNull();
    }
}

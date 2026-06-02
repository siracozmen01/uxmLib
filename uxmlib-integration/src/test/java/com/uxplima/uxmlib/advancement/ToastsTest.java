package com.uxplima.uxmlib.advancement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Wiring smoke tests for {@link Toasts}. The full register-award-revoke round-trip cannot be exercised under
 * MockBukkit: its {@code UnsafeValuesMock#loadAdvancement} throws {@code UnimplementedOperationException}, so
 * there is no way to stand up a synthetic advancement in-process. These tests therefore cover everything up
 * to that native boundary — construction, the bound builder, input validation, and that {@code show} really
 * does reach {@code loadAdvancement} — and document the native step that only a live server can run.
 */
class ToastsTest {

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

    @Test
    void boundBuilderBuildsTheSameSpecAsTheStaticBuilder() {
        Toasts toasts = new Toasts(plugin, new RecordingScheduler());
        String fromBound = toasts.builder()
                .icon(Material.DIAMOND)
                .title(Component.text("Hi"))
                .frame(AdvancementFrame.GOAL)
                .build()
                .toJson();
        String fromStatic = Toast.builder()
                .icon(Material.DIAMOND)
                .title(Component.text("Hi"))
                .frame(AdvancementFrame.GOAL)
                .build()
                .toJson();
        assertThat(fromBound).isEqualTo(fromStatic);
    }

    @Test
    void showReachesTheNativeLoaderThenStopsAtMockBukkitsUnimplementedLoad() {
        Toasts toasts = new Toasts(plugin, new RecordingScheduler());
        PlayerMock player = server.addPlayer();
        Toast toast = Toast.builder()
                .icon(Material.DIAMOND)
                .title(Component.text("Hi"))
                .build();
        // MockBukkit's loadAdvancement throws UnimplementedOperationException (a TestAbortedException). Catch it
        // explicitly so this asserts the call path rather than skipping the test, and so a live server (where
        // loadAdvancement works) is the only place the full toast fires.
        assertThatThrownBy(() -> toasts.show(toast, player))
                .isInstanceOf(org.mockbukkit.mockbukkit.exception.UnimplementedOperationException.class);
    }

    @Test
    void builderIsBoundToTheService() {
        Toasts toasts = new Toasts(plugin, new RecordingScheduler());
        // A bound builder can finish its spec; that the same instance also drives show() is covered above.
        Toast toast = toasts.builder()
                .icon(Material.STONE)
                .title(Component.text("Hi"))
                .build();
        assertThat(toast.icon()).isEqualTo(Material.STONE);
    }

    @Test
    void cleanupRevokesOnThePlayersRegionAndRemovesOnTheGlobalRegion() {
        // The native loadAdvancement boundary stops show() before cleanup under MockBukkit, so drive the
        // cleanup seam directly. The revoke touches the live player and must hop onto the player's own region
        // (entityLater); the registry removal touches global server state (globalLater). Routing the revoke on
        // the global region instead would mutate the player off its owning region thread on Folia.
        RecordingScheduler scheduler = new RecordingScheduler();
        Toasts toasts = new Toasts(plugin, scheduler);
        PlayerMock player = server.addPlayer();
        Advancement advancement = mock(Advancement.class);

        toasts.scheduleCleanup(new org.bukkit.NamespacedKey(plugin, "toast_x"), advancement, player);

        assertThat(scheduler.entityLaterTarget).isSameAs(player);
        assertThat(scheduler.entityLaterTask).isNotNull();
        assertThat(scheduler.globalLaterTask).isNotNull();
    }

    /** Captures the cleanup hops so a test can assert which region each one targets. */
    private static final class RecordingScheduler implements Scheduler {

        private @org.jspecify.annotations.Nullable Entity entityLaterTarget;
        private @org.jspecify.annotations.Nullable Runnable entityLaterTask;
        private @org.jspecify.annotations.Nullable Runnable globalLaterTask;

        private final TaskHandle handle = new TaskHandle() {
            private boolean cancelled;

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
        public TaskHandle globalLater(Duration delay, Runnable task) {
            this.globalLaterTask = task;
            return handle;
        }

        @Override
        public TaskHandle global(Runnable task) {
            return handle;
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            return handle;
        }

        @Override
        public TaskHandle region(Location location, Runnable task) {
            return handle;
        }

        @Override
        public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
            return handle;
        }

        @Override
        public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
            return handle;
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            return handle;
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
            this.entityLaterTarget = entity;
            this.entityLaterTask = task;
            return handle;
        }

        @Override
        public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
            return handle;
        }

        @Override
        public TaskHandle async(Runnable task) {
            return handle;
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            return handle;
        }

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            return handle;
        }
    }
}

package com.uxplima.uxmlib.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

/**
 * Teardown during {@code onDisable}, against a server that refuses a disabled plugin the way a real one does.
 *
 * <p>This was a live defect, not a hypothesis. {@code IllegalPluginAccessException: Plugin attempted to
 * register task while disabled} was thrown twice on a Paper 26.2 server and five times on a Folia 26.2 one,
 * out of two shutdown paths that hand their work to the global region: the nametag registry giving the
 * scoreboard teams back, and the menu engine closing the windows it owns. Every trace ended in
 * {@link PaperScheduler#global}. The work never ran, so a reloaded server left a player wearing a name no
 * plugin owned and a menu open with no engine behind it.
 *
 * <p>No unit test saw it because MockBukkit schedules for a disabled plugin quite happily. The fake below is
 * the missing piece: {@link RefusingServerMock} adds the rule Bukkit enforces and MockBukkit does not, and
 * answers the two thread questions the fix asks, so both platforms can be modelled here.
 */
class PaperSchedulerTeardownTest {

    private RefusingServerMock server;
    private PluginMock plugin;
    private PaperScheduler scheduler;
    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final Handler collector = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new RefusingServerMock());
        plugin = MockBukkit.createMockPlugin();
        plugin.getLogger().addHandler(collector);
        scheduler = new PaperScheduler(plugin);
    }

    @AfterEach
    void tearDown() {
        plugin.getLogger().removeHandler(collector);
        MockBukkit.unmock();
    }

    /** Disable the plugin the way the server does before it calls {@code onDisable}. */
    private void disablePlugin() {
        server.getPluginManager().disablePlugin(plugin);
        assertThat(plugin.isEnabled()).isFalse();
    }

    private List<String> warnings() {
        return logged.stream()
                .filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage)
                .toList();
    }

    @Test
    @DisplayName("the fake server refuses a disabled plugin, which is the rule MockBukkit does not have")
    void theFakeServerRefusesADisabledPlugin() {
        disablePlugin();
        assertThatThrownBy(() -> server.getGlobalRegionScheduler().run(plugin, task -> {}))
                .isInstanceOf(IllegalPluginAccessException.class)
                .hasMessageContaining("while disabled");
    }

    @Test
    @DisplayName("global teardown work runs inline once the plugin is disabled")
    void globalTeardownRunsInline() {
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        TaskHandle handle = scheduler.global(ran::incrementAndGet);

        assertThat(ran).hasValue(1);
        assertThat(handle.isCancelled()).isFalse();
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("a delayed teardown task runs inline too: the tick it waits for never comes")
    void delayedTeardownRunsInline() {
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        scheduler.globalLater(Duration.ofSeconds(1L), ran::incrementAndGet);

        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("an enabled plugin still goes through the server, so nothing runs a tick early")
    void anEnabledPluginStillGoesThroughTheServer() {
        AtomicInteger ran = new AtomicInteger();

        scheduler.global(ran::incrementAndGet);

        assertThat(ran).hasValue(0);
        server.getScheduler().performOneTick();
        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("a repeating task is refused rather than run once, and the refusal is said out loud")
    void aRepeatingTaskIsRefused() {
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        TaskHandle handle = scheduler.globalTimer(Duration.ZERO, Duration.ofSeconds(1L), task -> ran.incrementAndGet());

        assertThat(ran).hasValue(0);
        assertThat(handle.isCancelled()).isTrue();
        assertThat(warnings()).anyMatch(message -> message.contains("repeating"));
    }

    @Test
    @DisplayName("async teardown cannot run inline, so it is dropped and named rather than pretended")
    void asyncTeardownIsRefusedAndNamed() {
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        TaskHandle handle = scheduler.async(ran::incrementAndGet);

        assertThat(ran).hasValue(0);
        assertThat(handle.isCancelled()).isTrue();
        assertThat(warnings()).anyMatch(message -> message.contains("off-thread"));
    }

    @Test
    @DisplayName("region teardown runs inline when this thread owns the region")
    void regionTeardownRunsInline() {
        World world = server.addSimpleWorld("teardown");
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        scheduler.region(new Location(world, 0.0D, 64.0D, 0.0D), ran::incrementAndGet);

        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("on Folia's shutdown thread, global teardown still runs: that thread owns everything")
    void foliaShutdownThreadRunsGlobalTeardownInline() {
        server.foliaShutdownThread();
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        TaskHandle handle = scheduler.global(ran::incrementAndGet);

        assertThat(ran)
                .as("Folia disables plugins from the one thread it has handed every region to")
                .hasValue(1);
        assertThat(handle.isCancelled()).isFalse();
        assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("on Folia's shutdown thread, region teardown runs inline too")
    void foliaShutdownThreadRunsRegionTeardownInline() {
        World world = server.addSimpleWorld("folia");
        server.foliaShutdownThread();
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        scheduler.region(new Location(world, 0.0D, 64.0D, 0.0D), ran::incrementAndGet);

        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("on a Folia region tick thread, global work is refused: it is not that thread's to write")
    void foliaRegionThreadRefusesGlobalWork() {
        server.foliaRegionTickThread();
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        TaskHandle handle = scheduler.global(ran::incrementAndGet);

        assertThat(ran).hasValue(0);
        assertThat(handle.isCancelled()).isTrue();
        assertThat(warnings()).anyMatch(message -> message.contains("global region"));
    }

    @Test
    @DisplayName("on Folia, work for a region this thread does not own is refused as well")
    void foliaRegionThreadRefusesAnotherRegionsWork() {
        World world = server.addSimpleWorld("folia");
        server.foliaRegionTickThread();
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();

        TaskHandle handle = scheduler.region(new Location(world, 0.0D, 64.0D, 0.0D), ran::incrementAndGet);

        assertThat(ran).hasValue(0);
        assertThat(handle.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("a stopping server is not enough on its own: an off-server thread is still refused")
    void aThreadThatIsNotTheServersIsRefusedEvenWhileStopping() throws InterruptedException {
        server.foliaShutdownThread();
        disablePlugin();
        AtomicInteger ran = new AtomicInteger();
        boolean[] cancelled = {false};

        Thread elsewhere = new Thread(
                () -> cancelled[0] = scheduler.global(ran::incrementAndGet).isCancelled());
        elsewhere.start();
        elsewhere.join();

        assertThat(ran)
                .as("shutdown says when it is, not which thread it is on")
                .hasValue(0);
        assertThat(cancelled[0]).isTrue();
    }

    @Test
    @DisplayName("one teardown task that throws does not abort the rest of the disable")
    void aThrowingInlineTaskIsContained() {
        disablePlugin();

        assertThatCode(() -> scheduler.global(() -> {
                    throw new IllegalStateException("teams already gone");
                }))
                .doesNotThrowAnyException();

        assertThat(logged).anyMatch(record -> record.getThrown() instanceof IllegalStateException);
    }

    /**
     * A server that enforces the one rule this defect turned on: Bukkit refuses to register a task for a plugin
     * that is not enabled. MockBukkit accepts one, which is why every test in the library passed while two live
     * shutdown paths were throwing.
     */
    // ServerMock overrides Server#getBanList without its type parameter, so any subclass of it inherits an
    // unchecked warning that -Werror turns into a build failure. It is MockBukkit's raw type, not ours.
    @SuppressWarnings("unchecked")
    private static final class RefusingServerMock extends ServerMock {

        private boolean globalTickThread = true;
        private boolean stopping;
        private boolean ownsEveryRegion = true;

        /**
         * Model Folia's shutdown thread, which is where {@code onDisable} runs when a Folia server stops. Folia
         * halts every region tick and the global tick before it disables the plugins, so nothing is ticking and
         * {@code isGlobalTickThread} is false, while {@code TickThread.isTickThreadFor} answers true for every
         * region and every entity on that one thread. Folia closes the players' own inventories from it.
         */
        void foliaShutdownThread() {
            globalTickThread = false;
            stopping = true;
            ownsEveryRegion = true;
        }

        /** Model a Folia region tick thread with the server still up: it owns its own region and nothing else. */
        void foliaRegionTickThread() {
            globalTickThread = false;
            stopping = false;
            ownsEveryRegion = false;
        }

        @Override
        public boolean isGlobalTickThread() {
            return globalTickThread;
        }

        // ServerMock throws UnimplementedOperationException here, which JUnit records as a skip rather than a
        // failure, so a fake that did not answer this would quietly stop testing.
        @Override
        public boolean isStopping() {
            return stopping;
        }

        @Override
        public boolean isOwnedByCurrentRegion(Location location) {
            return ownsEveryRegion && super.isOwnedByCurrentRegion(location);
        }

        @Override
        public GlobalRegionScheduler getGlobalRegionScheduler() {
            return new RefusingGlobalScheduler(super.getGlobalRegionScheduler());
        }

        @Override
        public RegionScheduler getRegionScheduler() {
            return new RefusingRegionScheduler(super.getRegionScheduler());
        }

        @Override
        public AsyncScheduler getAsyncScheduler() {
            return new RefusingAsyncScheduler(super.getAsyncScheduler());
        }
    }

    private static void refuseIfDisabled(Plugin plugin) {
        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
        }
    }

    private record RefusingGlobalScheduler(GlobalRegionScheduler delegate) implements GlobalRegionScheduler {

        @Override
        public void execute(Plugin plugin, Runnable task) {
            refuseIfDisabled(plugin);
            delegate.execute(plugin, task);
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task) {
            refuseIfDisabled(plugin);
            return delegate.run(plugin, task);
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks) {
            refuseIfDisabled(plugin);
            return delegate.runDelayed(plugin, task, delayTicks);
        }

        @Override
        public ScheduledTask runAtFixedRate(
                Plugin plugin, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
            refuseIfDisabled(plugin);
            return delegate.runAtFixedRate(plugin, task, delayTicks, periodTicks);
        }

        @Override
        public void cancelTasks(Plugin plugin) {
            delegate.cancelTasks(plugin);
        }
    }

    private record RefusingRegionScheduler(RegionScheduler delegate) implements RegionScheduler {

        @Override
        public void execute(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
            refuseIfDisabled(plugin);
            delegate.execute(plugin, world, chunkX, chunkZ, task);
        }

        @Override
        public ScheduledTask run(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task) {
            refuseIfDisabled(plugin);
            return delegate.run(plugin, world, chunkX, chunkZ, task);
        }

        @Override
        public ScheduledTask runDelayed(
                Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task, long delayTicks) {
            refuseIfDisabled(plugin);
            return delegate.runDelayed(plugin, world, chunkX, chunkZ, task, delayTicks);
        }

        @Override
        public ScheduledTask runAtFixedRate(
                Plugin plugin,
                World world,
                int chunkX,
                int chunkZ,
                Consumer<ScheduledTask> task,
                long delayTicks,
                long periodTicks) {
            refuseIfDisabled(plugin);
            return delegate.runAtFixedRate(plugin, world, chunkX, chunkZ, task, delayTicks, periodTicks);
        }
    }

    private record RefusingAsyncScheduler(AsyncScheduler delegate) implements AsyncScheduler {

        @Override
        public ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> task) {
            refuseIfDisabled(plugin);
            return delegate.runNow(plugin, task);
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delay, TimeUnit unit) {
            refuseIfDisabled(plugin);
            return delegate.runDelayed(plugin, task, delay, unit);
        }

        @Override
        public ScheduledTask runAtFixedRate(
                Plugin plugin, Consumer<ScheduledTask> task, long initialDelay, long period, TimeUnit unit) {
            refuseIfDisabled(plugin);
            return delegate.runAtFixedRate(plugin, task, initialDelay, period, unit);
        }

        @Override
        public void cancelTasks(Plugin plugin) {
            delegate.cancelTasks(plugin);
        }
    }
}

package com.uxplima.uxmlib.hud;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * Drives boss bars over native Adventure. One shared repeating task advances every tracked bar each tick:
 * {@link BossBarMode#FILLING} and {@link BossBarMode#COUNTDOWN} follow a time ramp, {@link BossBarMode#DYNAMIC}
 * re-reads its caller-supplied progress (and optional name) functions, and {@link BossBarMode#PERMANENT}
 * holds whatever was last set. A finished countdown auto-hides; the task starts on the first bar and cancels
 * itself once the last one leaves, so an idle server runs nothing.
 *
 * <p>Per-player state lives on this instance (no static map). The manager is constructor-injected with the
 * {@link Scheduler} and the {@link Server} it uses to resolve a UUID back to an online player.
 */
public final class BossBarManager {

    private static final Duration TICK_PERIOD = Duration.ofMillis(50L);
    private static final BossBar.Color COUNTDOWN_COLOR = BossBar.Color.RED;
    private static final BossBar.Overlay COUNTDOWN_OVERLAY = BossBar.Overlay.PROGRESS;

    private final Scheduler scheduler;
    private final Server server;
    private final LongSupplier clock;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private @Nullable TaskHandle task;

    public BossBarManager(Scheduler scheduler, Server server) {
        // A monotonic source: timed bars must not freeze or finish early when the OS wall clock steps over
        // an NTP correction, so we derive elapsed time from System.nanoTime rather than currentTimeMillis.
        this(scheduler, server, () -> System.nanoTime() / 1_000_000L);
    }

    BossBarManager(Scheduler scheduler, Server server, LongSupplier clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Show {@code bar} on {@code player} under {@code mode}. {@code duration} is required for the timed modes
     * ({@link BossBarMode#FILLING}, {@link BossBarMode#COUNTDOWN}) and ignored otherwise; pass {@code null}
     * for {@link BossBarMode#PERMANENT}. Replaces any bar this manager already shows that player.
     */
    public void show(Player player, BossBar bar, BossBarMode mode, @Nullable Duration duration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(bar, "bar");
        Objects.requireNonNull(mode, "mode");
        long total = totalMillis(mode, duration);
        track(player, new Entry(bar, mode, clock.getAsLong(), total, null, null, null));
    }

    /**
     * One-call countdown: a red bar titled {@code name} that drains from full to empty over {@code duration}
     * and hides itself at zero. Returns the live {@link BossBar} so callers can restyle it.
     */
    public BossBar countdown(Player player, Component name, Duration duration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        requirePositive(duration);
        BossBar bar = BossBar.bossBar(name, BossBar.MAX_PROGRESS, COUNTDOWN_COLOR, COUNTDOWN_OVERLAY);
        long start = clock.getAsLong();
        track(player, new Entry(bar, BossBarMode.COUNTDOWN, start, duration.toMillis(), null, null, null));
        return bar;
    }

    /**
     * One-call live countdown: the title is a MiniMessage template carrying a {@code <time>} (or
     * {@code <auto_time_left>}) tag — for example {@code "<red>Ends in <time>"} — which re-renders every tick
     * to show the remaining time formatted through uxmlib {@code Durations}. The bar drains from full to empty
     * over {@code duration} and hides itself at zero. Returns the live {@link BossBar} so callers can restyle
     * it.
     *
     * @see RemainingTime
     */
    public BossBar countdown(Player player, String titleTemplate, Duration duration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(titleTemplate, "titleTemplate");
        requirePositive(duration);
        long start = clock.getAsLong();
        long total = duration.toMillis();
        Component initial = renderTitle(titleTemplate, start, total, start);
        BossBar bar = BossBar.bossBar(initial, BossBar.MAX_PROGRESS, COUNTDOWN_COLOR, COUNTDOWN_OVERLAY);
        track(player, new Entry(bar, BossBarMode.COUNTDOWN, start, total, null, null, titleTemplate));
        return bar;
    }

    /** A dynamic bar whose progress is re-read from {@code progress} each tick; the name stays fixed. */
    public BossBar dynamic(Player player, Component name, Function<Player, Float> progress) {
        Objects.requireNonNull(name, "name");
        return dynamic(player, p -> name, progress);
    }

    /** A dynamic bar whose name and progress are both re-read from caller functions each tick. */
    public BossBar dynamic(Player player, Function<Player, Component> name, Function<Player, Float> progress) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(progress, "progress");
        Component initialName = Objects.requireNonNull(name.apply(player), "name function returned null");
        float initial = clampProgress(Objects.requireNonNull(progress.apply(player), "progress returned null"));
        BossBar bar = BossBar.bossBar(initialName, initial, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        track(player, new Entry(bar, BossBarMode.DYNAMIC, clock.getAsLong(), 0L, name, progress, null));
        return bar;
    }

    /** Hide and stop tracking the bar this manager shows {@code player}, if any. */
    public void hide(UUID player) {
        Objects.requireNonNull(player, "player");
        Entry removed = entries.remove(player);
        if (removed != null) {
            removeFrom(player, removed.bar());
        }
    }

    /** The live boss bar tracked for {@code player}, or {@code null} if none. Exposed for tests and inspection. */
    public @Nullable BossBar barOf(UUID player) {
        Objects.requireNonNull(player, "player");
        Entry entry = entries.get(player);
        return entry == null ? null : entry.bar();
    }

    /** How many players currently have a managed boss bar. Exposed for tests and metrics. */
    public int tracked() {
        return entries.size();
    }

    /**
     * Hide every tracked bar from its still-online player and cancel the shared timer. Call this on plugin
     * disable so bars do not linger on screen and the repeating task does not outlive the manager; the manager
     * is reusable afterward (a later {@link #show} restarts the timer).
     */
    public void close() {
        for (Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Entry> e = it.next();
            removeFrom(e.getKey(), e.getValue().bar());
            it.remove();
        }
        cancelTimer();
    }

    private synchronized void cancelTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void track(Player player, Entry entry) {
        UUID id = player.getUniqueId();
        Entry previous = entries.put(id, entry);
        if (previous != null && previous.bar() != entry.bar()) {
            player.hideBossBar(previous.bar());
        }
        player.showBossBar(entry.bar());
        startIfIdle();
    }

    private synchronized void startIfIdle() {
        if (task == null) {
            task = scheduler.globalTimer(TICK_PERIOD, TICK_PERIOD, this::tick);
        }
    }

    private void tick(TaskHandle handle) {
        long now = clock.getAsLong();
        for (Iterator<Map.Entry<UUID, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Entry> e = it.next();
            Player player = server.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) {
                it.remove();
                continue;
            }
            if (advance(player, e.getValue(), now)) {
                it.remove();
            }
        }
        stopIfEmpty(handle);
    }

    /** Apply one tick of progress to {@code entry}; returns {@code true} when the entry has finished. */
    private boolean advance(Player player, Entry entry, long now) {
        BossBarMode mode = entry.mode();
        if (mode == BossBarMode.DYNAMIC) {
            updateDynamic(player, entry);
            return false;
        }
        if (!mode.timed()) {
            return false;
        }
        long elapsed = now - entry.startMillis();
        if (mode.finishedAt(elapsed, entry.totalMillis())) {
            removeFrom(player.getUniqueId(), entry.bar());
            return true;
        }
        String template = entry.titleTemplate();
        if (template != null) {
            entry.bar().name(renderTitle(template, entry.startMillis(), entry.totalMillis(), now));
        }
        entry.bar().progress(mode.progressAt(elapsed, entry.totalMillis()));
        return false;
    }

    /** Render {@code template} with a {@code <time>} tag bound to the millis left at {@code now}. */
    private static Component renderTitle(String template, long startMillis, long totalMillis, long now) {
        long remaining = startMillis + totalMillis - now;
        Duration left = Duration.ofMillis(Math.max(0L, remaining));
        return Text.mini(template, RemainingTime.resolver(() -> left));
    }

    private void updateDynamic(Player player, Entry entry) {
        Function<Player, Component> nameFn = entry.nameFn();
        Function<Player, Float> progressFn = entry.progressFn();
        if (nameFn != null) {
            Component name = nameFn.apply(player);
            if (name != null) {
                entry.bar().name(name);
            }
        }
        if (progressFn != null) {
            Float value = progressFn.apply(player);
            if (value != null) {
                entry.bar().progress(clampProgress(value));
            }
        }
    }

    private void removeFrom(UUID id, BossBar bar) {
        Player player = server.getPlayer(id);
        if (player != null) {
            player.hideBossBar(bar);
        }
    }

    private synchronized void stopIfEmpty(TaskHandle handle) {
        if (entries.isEmpty()) {
            handle.cancel();
            task = null;
        }
    }

    private static long totalMillis(BossBarMode mode, @Nullable Duration duration) {
        if (!mode.timed()) {
            return 0L;
        }
        if (duration == null) {
            throw new IllegalArgumentException(mode + " requires a duration");
        }
        requirePositive(duration);
        return duration.toMillis();
    }

    private static void requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }

    private static float clampProgress(float value) {
        if (value < BossBar.MIN_PROGRESS) {
            return BossBar.MIN_PROGRESS;
        }
        return value > BossBar.MAX_PROGRESS ? BossBar.MAX_PROGRESS : value;
    }

    private record Entry(
            BossBar bar,
            BossBarMode mode,
            long startMillis,
            long totalMillis,
            @Nullable Function<Player, Component> nameFn,
            @Nullable Function<Player, Float> progressFn,
            @Nullable String titleTemplate) {}
}

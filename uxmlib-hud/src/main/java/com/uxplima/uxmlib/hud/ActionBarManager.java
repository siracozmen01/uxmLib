package com.uxplima.uxmlib.hud;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * A sticky action bar. Vanilla fades an action-bar message after about two seconds, so a single shared
 * repeating task re-sends each tracked player's message until its deadline passes. The re-send interval is
 * well under the fade window, so the line never visibly blinks. The shared task starts when the first
 * player is tracked and cancels itself once the last entry expires, so an idle server runs no task.
 *
 * <p>Per-player state lives on this instance (no static map); the manager is constructor-injected with the
 * {@link Scheduler} and the {@link Server} used to resolve a UUID back to an online player.
 */
public final class ActionBarManager {

    private static final Duration TICK_PERIOD = Duration.ofMillis(30L * 50L);

    private final Scheduler scheduler;
    private final Server server;
    private final LongSupplier clock;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private @Nullable TaskHandle task;

    public ActionBarManager(Scheduler scheduler, Server server) {
        // A monotonic source: a sticky bar's deadline must not stall (or expire early) when the OS wall clock
        // steps over an NTP correction, so we measure against System.nanoTime rather than currentTimeMillis.
        this(scheduler, server, () -> System.nanoTime() / 1_000_000L);
    }

    ActionBarManager(Scheduler scheduler, Server server, LongSupplier clock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Keep {@code message} on {@code player}'s action bar for {@code duration}, re-sending until it lapses. */
    public void show(Player player, Component message, Duration duration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        entries.put(player.getUniqueId(), new Entry(message, null, clock.getAsLong() + duration.toMillis()));
        player.sendActionBar(message);
        startIfIdle();
    }

    /**
     * A sticky countdown line: {@code titleTemplate} is a MiniMessage template carrying a {@code <time>} (or
     * {@code <auto_time_left>}) tag — for example {@code "<gray>Closing in <time>"} — re-rendered on every
     * re-send to show the remaining time formatted through uxmlib {@code Durations}. The line lapses when
     * {@code duration} runs out.
     *
     * @see RemainingTime
     */
    public void countdown(Player player, String titleTemplate, Duration duration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(titleTemplate, "titleTemplate");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        long until = clock.getAsLong() + duration.toMillis();
        entries.put(player.getUniqueId(), new Entry(null, titleTemplate, until));
        player.sendActionBar(render(titleTemplate, until, clock.getAsLong()));
        startIfIdle();
    }

    /** Stop the sticky action bar for {@code player}; the line then fades out on its own. */
    public void clear(UUID player) {
        Objects.requireNonNull(player, "player");
        entries.remove(player);
    }

    /** How many players currently have a sticky action bar. Exposed for tests and metrics. */
    public int tracked() {
        return entries.size();
    }

    /**
     * Stop tracking every sticky bar and cancel the shared timer. Call this on plugin disable so the repeating
     * re-send task does not outlive the manager; the lines fade out on their own within the vanilla fade
     * window. The manager is reusable afterward (a later {@link #show} restarts the timer).
     */
    public void close() {
        entries.clear();
        cancelTimer();
    }

    private synchronized void cancelTimer() {
        if (task != null) {
            task.cancel();
            task = null;
        }
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
            if (now >= e.getValue().untilMillis()) {
                it.remove();
                continue;
            }
            resend(e.getKey(), e.getValue(), now);
        }
        stopIfEmpty(handle);
    }

    private void resend(UUID id, Entry entry, long now) {
        Player player = server.getPlayer(id);
        if (player != null && player.isOnline()) {
            player.sendActionBar(messageOf(entry, now));
        } else {
            entries.remove(id);
        }
    }

    private static Component messageOf(Entry entry, long now) {
        String template = entry.template();
        Component fixed = entry.message();
        return template != null ? render(template, entry.untilMillis(), now) : Objects.requireNonNull(fixed);
    }

    /** Render {@code template} with a {@code <time>} tag bound to the millis left until {@code untilMillis}. */
    private static Component render(String template, long untilMillis, long now) {
        Duration left = Duration.ofMillis(Math.max(0L, untilMillis - now));
        return Text.mini(template, RemainingTime.resolver(() -> left));
    }

    private synchronized void stopIfEmpty(TaskHandle handle) {
        if (entries.isEmpty()) {
            handle.cancel();
            task = null;
        }
    }

    private record Entry(@Nullable Component message, @Nullable String template, long untilMillis) {}
}

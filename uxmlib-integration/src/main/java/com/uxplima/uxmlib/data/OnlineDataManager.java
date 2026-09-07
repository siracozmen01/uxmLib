package com.uxplima.uxmlib.data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * The opinionated online-data lifecycle: load a player's value on join, keep it in memory while they are
 * online, save it on quit, and flush every online value on a periodic timer. It turns the storage primitives
 * (a {@link DataStore} the consumer wires to their backend) into the shape plugins actually want: a cache
 * that mirrors who is online and persists without per-call bookkeeping.
 *
 * <p><b>Architecture.</b> The manager writes through the generic {@link DataStore} seam rather than depending
 * on {@code uxmlib-storage}, so it stays in {@code uxmlib-integration} without pulling the JDBC stack onto
 * consumers. The consumer points the seam at whatever they run (a {@code Repository}, a
 * {@code WriteBehindStorage}, a file) and calls {@link #installListener} once on enable to wire the Bukkit
 * join/quit events; {@link #start} begins the periodic flush.
 *
 * <p><b>Threading.</b> Every {@link DataStore} call runs off the main thread on {@link Scheduler#async}: the
 * load on join, the save on quit, and the periodic flush, so a blocking backend never stalls the server, and
 * the manager is Folia-ready because all scheduling goes through the library {@link Scheduler}. The cache is a
 * {@link ConcurrentHashMap}, so the async hand-back of a loaded value and the main-thread {@link #get} reads
 * are safe without extra locking.
 *
 * <p><b>Flush policy.</b> The periodic flush saves <em>all</em> currently-online values (it does not consult a
 * dirty flag); the value type need not expose one. This is intentionally simple and safe: at worst it writes
 * an unchanged value. A consumer that wants dirty-only flushing can layer that into its {@link DataStore}.
 *
 * <p>No static mutable state: the cache, the timer handle and the listeners all live on the instance.
 *
 * @param <V> the per-player value type
 */
public final class OnlineDataManager<V> {

    private final Scheduler scheduler;
    private final DataStore<V> store;
    private final Duration flushInterval;
    private final Consumer<Throwable> errors;
    private final Map<UUID, V> cache = new ConcurrentHashMap<>();

    private volatile @Nullable TaskHandle flushTask;

    /**
     * Create a manager whose store failures are reported to {@code plugin}'s logger. The flush runs every
     * {@code flushInterval}; {@code flushInterval} must be positive.
     */
    public OnlineDataManager(Plugin plugin, Scheduler scheduler, DataStore<V> store, Duration flushInterval) {
        this(scheduler, store, flushInterval, loggerSink(Objects.requireNonNull(plugin, "plugin")));
    }

    /**
     * Create a manager with an explicit error sink. Used by tests (no live plugin) and by a consumer that
     * wants to route store failures somewhere other than the plugin logger. {@code flushInterval} must be
     * positive.
     */
    public OnlineDataManager(
            Scheduler scheduler, DataStore<V> store, Duration flushInterval, Consumer<Throwable> errors) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.store = Objects.requireNonNull(store, "store");
        this.flushInterval = Objects.requireNonNull(flushInterval, "flushInterval");
        this.errors = Objects.requireNonNull(errors, "errors");
        if (flushInterval.isZero() || flushInterval.isNegative()) {
            throw new IllegalArgumentException("flushInterval must be positive: " + flushInterval);
        }
    }

    /**
     * Register the single Bukkit listener that drives load-on-join and save-on-quit. Call this once on plugin
     * enable. The periodic flush is separate: call {@link #start} to begin it.
     */
    public void installListener(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(new OnlineDataListener<>(this), plugin);
    }

    /** Start the periodic flush timer. Idempotent: a second call while running is a no-op. */
    public void start() {
        if (!isRunning()) {
            flushTask = scheduler.asyncTimer(flushInterval, flushInterval, handle -> flush());
        }
    }

    /** Stop the periodic flush timer. Does not save or evict: use {@link #flush} / {@link #handleQuit} for that. */
    public void stop() {
        TaskHandle current = flushTask;
        if (current != null) {
            current.cancel();
            flushTask = null;
        }
    }

    /** Whether the periodic flush timer is live. */
    public boolean isRunning() {
        TaskHandle current = flushTask;
        return current != null && !current.isCancelled();
    }

    /**
     * Load {@code player}'s value off-thread and cache it. Called by the listener on join. A failing load is
     * routed to the error sink and leaves the player unloaded (a later join retries cleanly).
     */
    public void handleJoin(UUID player) {
        Objects.requireNonNull(player, "player");
        scheduler.async(() -> {
            try {
                cache.put(player, store.load(player));
            } catch (RuntimeException failure) {
                errors.accept(failure);
            }
        });
    }

    /**
     * Save {@code player}'s cached value off-thread, then evict it. Called by the listener on quit. A no-op if
     * the player was never loaded (e.g. their join load failed). A failing save is routed to the error sink;
     * the entry is still evicted so a departed UUID never lingers in the cache.
     *
     * <p>This is the one save in the library that a plugin must not leave to its own {@code onDisable}. The work
     * is off-thread by design, and {@code PaperScheduler} will neither schedule it for a disabled plugin nor run
     * it on a server thread instead: it drops it and says so. A shutdown calls {@link #stop} and then
     * {@link #flush}, which saves every cached value on the calling thread. The quit path itself is unaffected,
     * because the server has already emptied the roster by the time it disables anything.
     */
    public void handleQuit(UUID player) {
        Objects.requireNonNull(player, "player");
        V value = cache.remove(player);
        if (value != null) {
            scheduler.async(() -> saveQuietly(player, value));
        }
    }

    /**
     * Save every currently-online value, keeping them cached. Run on the flush timer and safe to call
     * directly. A save that throws is reported and skipped so one bad entry never aborts the sweep. Snapshots
     * the cache first so a concurrent join/quit cannot disturb the pass.
     */
    public void flush() {
        for (Map.Entry<UUID, V> entry : new ArrayList<>(cache.entrySet())) {
            saveQuietly(entry.getKey(), entry.getValue());
        }
    }

    /**
     * The cached value for an online {@code player}, or {@code null} if they are not loaded. Safe to call on
     * the main thread; mutate the returned value in place and it will be persisted on the next flush or quit.
     */
    public @Nullable V get(UUID player) {
        return cache.get(Objects.requireNonNull(player, "player"));
    }

    /** Whether {@code player}'s value is currently cached (loaded and not yet evicted). */
    public boolean isLoaded(UUID player) {
        return cache.containsKey(Objects.requireNonNull(player, "player"));
    }

    /** How many players' values are currently cached. */
    public int onlineCount() {
        return cache.size();
    }

    private void saveQuietly(UUID player, V value) {
        try {
            store.save(player, value);
        } catch (RuntimeException failure) {
            errors.accept(failure);
        }
    }

    private static Consumer<Throwable> loggerSink(Plugin plugin) {
        return failure -> plugin.getLogger().log(Level.WARNING, "online data store operation failed", failure);
    }
}

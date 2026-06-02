package com.uxplima.uxmlib.hologram.pool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * Auto show/hides each registered hologram per-player by distance, so a consumer registers a hologram and
 * forgets it — no manual viewer bookkeeping. On a repeating {@link Scheduler} task the pool snapshots the
 * online players in each hologram's world, asks the registered {@link VisibilityGate} which of them should
 * see it (same world, within range, optionally inside the FOV cone), diffs that against the set it last
 * showed, and calls the hologram's existing {@code show}/{@code hide} only on the transition.
 *
 * <p>The visibility pass runs on the global region (reading locations there is safe on Paper and Folia);
 * every {@code show}/{@code hide} is then bounced onto the hologram entity's own region thread through the
 * {@link Scheduler}, which is the fix for the Folia-unsafe single-async-loop pattern this is modelled on.
 *
 * <p>The pool owns the per-hologram viewer cache. Registering with a radius starts the timer if it was
 * idle; unregistering hides the hologram from everyone the pool was showing it to, and the timer stops
 * once nothing is registered, so an empty pool costs nothing.
 */
public final class HologramPool {

    private static final Duration DEFAULT_WARMUP = Duration.ofSeconds(1);
    private static final Duration DEFAULT_PERIOD = Duration.ofMillis(100);

    private final Scheduler scheduler;
    private final Duration warmup;
    private final Duration period;
    private final ViewerSink sink;
    private final NearbyPlayers nearby;
    private final Map<Hologram, Tracked> tracked = new ConcurrentHashMap<>();

    private volatile @Nullable TaskHandle task;

    /** Create a pool that ticks every 100&nbsp;ms after a one-second warmup. */
    public HologramPool(Plugin plugin, Scheduler scheduler) {
        this(plugin, scheduler, DEFAULT_WARMUP, DEFAULT_PERIOD);
    }

    /** Create a pool with a custom warmup delay and tick period. */
    public HologramPool(Plugin plugin, Scheduler scheduler, Duration warmup, Duration period) {
        Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.warmup = Objects.requireNonNull(warmup, "warmup");
        this.period = Objects.requireNonNull(period, "period");
        this.sink = new SchedulerViewerSink(plugin, scheduler);
        this.nearby = HologramPool::desiredViewers;
    }

    // Test seam: inject the player-supplier and the show/hide sink so the register/tick/diff lifecycle can
    // be driven against a fake hologram with no live entity or region thread.
    HologramPool(Scheduler scheduler, NearbyPlayers nearby, ViewerSink sink) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.warmup = DEFAULT_WARMUP;
        this.period = DEFAULT_PERIOD;
        this.nearby = Objects.requireNonNull(nearby, "nearby");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Register {@code hologram} so the pool shows it to any player within {@code radius} blocks of it (same
     * world only) and hides it from anyone who leaves that range. Re-registering updates the radius. Starts
     * the visibility task if it was idle. {@code radius} must be positive.
     */
    public void register(Hologram hologram, double radius) {
        register(hologram, VisibilityGate.range(radius));
    }

    /**
     * Register {@code hologram} with an explicit {@link VisibilityGate} so the pool can also FOV-cull — show
     * it only to players within range <em>and</em> looking toward it. Re-registering swaps the gate. Starts
     * the visibility task if it was idle.
     */
    public void register(Hologram hologram, VisibilityGate gate) {
        Objects.requireNonNull(hologram, "hologram");
        Objects.requireNonNull(gate, "gate");
        tracked.compute(
                hologram, (key, existing) -> existing == null ? new Tracked(key, gate) : existing.withGate(gate));
        ensureRunning();
    }

    /**
     * Stop managing {@code hologram} and hide it from everyone the pool was currently showing it to. The
     * hologram itself is not despawned — that stays the caller's (or {@code HologramManager}'s) job. Stops
     * the visibility task when nothing is left registered. A no-op if it was not registered.
     */
    public void unregister(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        Tracked removed = tracked.remove(hologram);
        if (removed != null) {
            for (UUID viewer : removed.snapshotViewers()) {
                sink.hide(hologram, viewer);
            }
            if (tracked.isEmpty()) {
                stop();
            }
        }
    }

    /** How many holograms the pool is currently managing. */
    public int size() {
        return tracked.size();
    }

    /** Whether the visibility task is live. */
    public boolean isRunning() {
        TaskHandle current = task;
        return current != null && !current.isCancelled();
    }

    private void ensureRunning() {
        if (!isRunning() && !tracked.isEmpty()) {
            task = scheduler.globalTimer(warmup, period, handle -> tick());
        }
    }

    private void stop() {
        TaskHandle current = task;
        if (current != null) {
            current.cancel();
            task = null;
        }
    }

    /**
     * One visibility pass over every tracked hologram. Snapshots the tracked set first so a mid-tick
     * register/unregister cannot disturb the loop. Package-private so a test can drive a single pass.
     */
    void tick() {
        for (Tracked entry : new ArrayList<>(tracked.values())) {
            evaluate(entry);
        }
    }

    private void evaluate(Tracked entry) {
        applyDelta(entry, nearby.desiredFor(entry.hologram(), entry.gate()));
    }

    /**
     * Production {@link NearbyPlayers}: read the hologram's current world and the players in it, then keep
     * the ones the gate clears (same world, in range, and — if the gate has FOV — looking toward it). A
     * hologram with no world (despawned mid-tick) sees nobody. The viewer's eye location feeds the cone.
     */
    private static Set<UUID> desiredViewers(Hologram hologram, VisibilityGate gate) {
        Location origin = hologram.entity().getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return Set.of();
        }
        Set<UUID> desired = new HashSet<>();
        for (Player player : new ArrayList<>(world.getPlayers())) {
            if (gate.shouldShow(player.getEyeLocation(), origin)) {
                desired.add(player.getUniqueId());
            }
        }
        return desired;
    }

    private void applyDelta(Tracked entry, Set<UUID> desired) {
        Set<UUID> current = entry.snapshotViewers();
        for (UUID viewer : HologramVisibility.toShow(current, desired)) {
            entry.addViewer(viewer);
            sink.show(entry.hologram(), viewer);
        }
        for (UUID viewer : HologramVisibility.toHide(current, desired)) {
            entry.removeViewer(viewer);
            sink.hide(entry.hologram(), viewer);
        }
    }

    /** A hologram under management plus its visibility gate and the pool-owned set it is shown to. */
    private static final class Tracked {
        private final Hologram hologram;
        private final VisibilityGate gate;
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

        Tracked(Hologram hologram, VisibilityGate gate) {
            this.hologram = hologram;
            this.gate = gate;
        }

        Tracked withGate(VisibilityGate newGate) {
            Tracked copy = new Tracked(hologram, newGate);
            copy.viewers.addAll(viewers);
            return copy;
        }

        Hologram hologram() {
            return hologram;
        }

        VisibilityGate gate() {
            return gate;
        }

        Set<UUID> snapshotViewers() {
            return Set.copyOf(viewers);
        }

        void addViewer(UUID viewer) {
            viewers.add(viewer);
        }

        void removeViewer(UUID viewer) {
            viewers.remove(viewer);
        }
    }
}

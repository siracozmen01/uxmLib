package com.uxplima.uxmlib.hologram;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

/**
 * Tracks the holograms a plugin spawns so they can all be despawned at once. A {@link TextDisplay}
 * hologram is a persistent world entity: if the plugin disables or reloads without removing it, it
 * lingers as an orphan. Spawn through a manager and call {@link #removeAll()} from {@code onDisable}
 * (and before re-spawning on reload) and that cannot happen.
 *
 * <p>The tracked set is concurrent, so spawning and removing from different region threads is safe; the
 * individual {@link Hologram#remove()} and {@link Hologram#setText} calls must still run on each entity's
 * own region thread (Folia), as documented on {@link Hologram}.
 */
public final class HologramManager {

    private final Set<Hologram> tracked = ConcurrentHashMap.newKeySet();
    private final List<HologramLifecycle> lifecycles = new CopyOnWriteArrayList<>();

    /** Install the built-in lifecycle that keeps the tracked-hologram viewer caches honest. */
    public HologramManager() {
        registerLifecycle(new ViewerInvalidation());
    }

    /** Spawn {@code builder} at {@code location} and track the result. Must run on the region thread. */
    public Hologram spawn(Holograms.Builder builder, Location location) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(location, "location");
        return track(builder.spawnAt(location));
    }

    /** Track a hologram spawned elsewhere so {@link #removeAll()} will despawn it too. */
    public Hologram track(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        tracked.add(hologram);
        return hologram;
    }

    /** Despawn one tracked hologram now and stop tracking it. */
    public void remove(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (tracked.remove(hologram)) {
            hologram.remove();
        }
    }

    /** How many holograms are currently tracked. */
    public int count() {
        return tracked.size();
    }

    /**
     * Install the single Bukkit listener that drives the lifecycle SPI. It forwards every join / quit /
     * respawn / world-change to the registered {@link HologramLifecycle} widgets (and the built-in viewer
     * invalidation), so a consumer wires this once on enable and every widget gets its callbacks for free.
     * Native entities don't need a packet re-send, but our {@code Set<UUID>} viewer caches and any per-player
     * widget state would otherwise hold a departed UUID or stale visibility forever.
     */
    public void installLifecycleListener(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(new HologramLifecycleListener(this), plugin);
    }

    /**
     * Register a widget's {@link HologramLifecycle} so the manager fans player events out to it. A widget
     * (paged / leaderboard / switchable) calls this once when it is created and {@link #unregisterLifecycle}
     * when it is removed, so its per-player state resets centrally without its own Bukkit listener.
     */
    public void registerLifecycle(HologramLifecycle lifecycle) {
        lifecycles.add(Objects.requireNonNull(lifecycle, "lifecycle"));
    }

    /** Stop fanning player events out to {@code lifecycle}. A no-op if it was not registered. */
    public void unregisterLifecycle(HologramLifecycle lifecycle) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        lifecycles.remove(lifecycle);
    }

    /** Fan a join out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchJoin(UUID player) {
        fanOut(player, HologramLifecycle::onJoin);
    }

    /** Fan a quit out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchQuit(UUID player) {
        fanOut(player, HologramLifecycle::onQuit);
    }

    /** Fan a world-change out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchWorldChange(UUID player) {
        fanOut(player, HologramLifecycle::onWorldChange);
    }

    /** Fan a respawn out to every registered lifecycle. Called by the Bukkit listener. */
    public void dispatchRespawn(UUID player) {
        fanOut(player, HologramLifecycle::onRespawn);
    }

    private void fanOut(UUID player, java.util.function.BiConsumer<HologramLifecycle, UUID> hook) {
        Objects.requireNonNull(player, "player");
        for (HologramLifecycle lifecycle : lifecycles) {
            try {
                hook.accept(lifecycle, player);
            } catch (RuntimeException failure) {
                // One widget's broken hook must never stop the others (or leak the viewer cache); isolate it.
            }
        }
    }

    /** Drop {@code viewer} from every tracked hologram's allowed-viewer set. */
    public void invalidateViewer(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        for (Hologram hologram : tracked) {
            hologram.forgetViewer(viewer);
        }
    }

    /**
     * The built-in lifecycle: a quit, respawn or world-change drops the player from every tracked hologram's
     * viewer set so the per-UUID cache neither leaks a departed player nor holds a since-moved one. This is
     * the behaviour the old standalone listener had, now expressed as a registered SPI member.
     */
    private final class ViewerInvalidation implements HologramLifecycle {
        @Override
        public void onQuit(UUID player) {
            invalidateViewer(player);
        }

        @Override
        public void onWorldChange(UUID player) {
            invalidateViewer(player);
        }

        @Override
        public void onRespawn(UUID player) {
            invalidateViewer(player);
        }
    }

    /** Despawn every tracked hologram and clear the set. Call this from {@code onDisable}. */
    public void removeAll() {
        for (Hologram hologram : tracked) {
            hologram.remove();
        }
        tracked.clear();
    }

    /**
     * Remove every uxmLib-marked {@link org.bukkit.entity.Display} in {@code world} that this manager is
     * not tracking — the holograms a previous run left behind after a crash (text displays persist). Run
     * this once shortly after enable, on the region/global thread. Returns how many were swept.
     */
    public int sweepOrphans(org.bukkit.World world) {
        Objects.requireNonNull(world, "world");
        int swept = 0;
        for (org.bukkit.entity.Display display : world.getEntitiesByClass(org.bukkit.entity.Display.class)) {
            if (Markers.isHologram(display)) {
                display.remove();
                swept++;
            }
        }
        return swept;
    }
}

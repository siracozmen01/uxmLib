package com.uxplima.uxmlib.hologram;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

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

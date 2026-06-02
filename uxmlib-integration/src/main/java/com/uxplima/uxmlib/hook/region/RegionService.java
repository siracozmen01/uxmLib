package com.uxplima.uxmlib.hook.region;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.hook.PluginHook;

/**
 * A provider-agnostic view of a region / land-claim plugin (WorldGuard, Towny, …). A caller asks whether a
 * player may act at a point and which regions cover it, without depending on any one plugin; an
 * implementation answers from the backing plugin's claim model. Every method takes a live {@link Location}
 * or {@link Player}, so a provider can resolve the world and claim on demand.
 *
 * <p>These queries read the backing plugin's in-memory region/claim state, which is cheap for the
 * region-set query but may consult per-player permission caches for the build/interact checks. Treat them as
 * main-thread-safe reads (they touch the Bukkit API), not as work to fan out across threads.
 */
public interface RegionService extends PluginHook {

    /** Whether {@code player} is permitted to place or break a block at {@code location}. */
    boolean canBuild(Player player, Location location);

    /** Whether {@code player} is permitted to interact with (use/switch) a block at {@code location}. */
    boolean canInteract(Player player, Location location);

    /**
     * The identifiers of every region/claim covering {@code location}, or an empty set when none do. The
     * meaning of an identifier is provider-specific (a WorldGuard region id, a Towny town name).
     */
    Set<String> regionsAt(Location location);

    /** Whether {@code location} lies in unclaimed/unprotected land (no region or claim covers it). */
    boolean isWilderness(Location location);
}

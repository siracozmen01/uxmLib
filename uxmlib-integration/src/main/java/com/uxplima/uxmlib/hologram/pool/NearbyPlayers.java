package com.uxplima.uxmlib.hologram.pool;

import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmlib.hologram.Hologram;

/**
 * Computes the set of players who should currently see a hologram — same world, within range, and inside the
 * FOV cone if the {@link VisibilityGate} has one. Pulled out as a seam so the {@link HologramPool}'s diff
 * lifecycle can run against a fake hologram and a canned player set in tests, while production reads the live
 * entity location and the players in its world.
 */
@FunctionalInterface
interface NearbyPlayers {

    /** The UUIDs that should see {@code hologram} given the registered {@code gate}. */
    Set<UUID> desiredFor(Hologram hologram, VisibilityGate gate);
}

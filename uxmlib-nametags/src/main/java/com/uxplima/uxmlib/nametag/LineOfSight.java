package com.uxplima.uxmlib.nametag;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The seam between the renderer's fade-through-blocks logic and the actual block ray-trace. The renderer asks,
 * per viewer per refresh, whether the line from that viewer to the target is blocked; the real implementation
 * answers with Bukkit, and a test answers with a fake. Keeping the ray-trace behind this interface is what lets
 * {@link NametagRenderer}'s fade logic run under a pure unit test with no world.
 *
 * <p>Called only on the target's region thread, inside the refresh tick, so implementations may read the world.
 */
@FunctionalInterface
public interface LineOfSight {

    /**
     * Whether {@code viewer}'s view of {@code target} is blocked by solid geometry (so the nametag should fade).
     *
     * @param viewer the player looking toward the target
     * @param target the entity the nametag rides
     * @return {@code true} when a solid block sits between the viewer and the target
     */
    boolean obstructed(Player viewer, Entity target);
}

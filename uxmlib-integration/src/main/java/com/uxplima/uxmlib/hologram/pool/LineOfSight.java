package com.uxplima.uxmlib.hologram.pool;

import java.util.Objects;
import java.util.function.BiPredicate;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Builds the line-of-sight predicate a {@link VisibilityGate} consults: a {@code (eye, hologram)} test that
 * returns whether a solid block sits between the two points. The ray-trace is a world read, so the resulting
 * predicate must only be invoked on the region thread that owns the world (never async); the gate already
 * gates it behind the cheap range/FOV checks.
 *
 * <p>The cast itself ignores fluids and passable blocks (tall grass, signs) so only real geometry occludes,
 * matching what a player can actually see through.
 */
public final class LineOfSight {

    private LineOfSight() {}

    /**
     * A predicate that is {@code true} when nothing solid blocks the straight line from {@code eye} to the
     * hologram. Returns {@code false} across worlds or when either world is null, so it composes safely with
     * the gate's same-world range check. A zero-length offset (viewer on the hologram) is always clear.
     */
    public static BiPredicate<Location, Location> rayTrace() {
        return LineOfSight::clearSightline;
    }

    private static boolean clearSightline(Location eye, Location hologram) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(hologram, "hologram");
        World world = eye.getWorld();
        if (world == null || !world.equals(hologram.getWorld())) {
            return false;
        }
        Vector toHologram = hologram.toVector().subtract(eye.toVector());
        double distance = toHologram.length();
        if (distance < 1.0e-4) {
            return true;
        }
        return world.rayTraceBlocks(eye, toHologram, distance, FluidCollisionMode.NEVER, true) == null;
    }
}

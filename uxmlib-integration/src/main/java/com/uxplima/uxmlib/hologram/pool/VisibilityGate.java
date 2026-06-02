package com.uxplima.uxmlib.hologram.pool;

import java.util.Objects;
import java.util.function.BiPredicate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import org.jspecify.annotations.Nullable;

/**
 * The single pure check that decides whether one viewer should currently see a hologram. Centralizing it
 * here keeps the rule in one place: a {@link ViewerLifecycle} reconciles intent against exactly this gate,
 * and {@link HologramPool} can use the range-only form too. A gate combines a same-world range cull with an
 * optional field-of-view cone and an optional line-of-sight check, so a hologram only shows when the viewer
 * is within reach, looking at it, and not occluded by terrain.
 *
 * <p>The cone test is the dot-product technique from NPCLib ({@code NPCBase#inViewOf}, MIT): the cosine of
 * the angle between the viewer's eye direction and the direction to the hologram equals the dot of those two
 * unit vectors, so {@code dot >= cos(halfAngle)} is a branch-free "is it inside the cone" test.
 *
 * <p>The line-of-sight check is left as an injected {@code (eye, hologram) -> hasClearSight} predicate rather
 * than ray-tracing inline: a ray-trace is a world read that must run on the region thread, so the caller
 * supplies one backed by {@link World#rayTraceBlocks}, and tests pass a deterministic stub. The predicate is
 * only consulted once range (and any FOV) already passed, so the expensive read is gated behind the cheap math.
 */
public final class VisibilityGate {

    private final double radiusSquared;
    private final double cosFov;
    private final boolean fovEnabled;
    private final @Nullable BiPredicate<Location, Location> lineOfSight;

    private VisibilityGate(
            double radiusSquared,
            double cosFov,
            boolean fovEnabled,
            @Nullable BiPredicate<Location, Location> lineOfSight) {
        this.radiusSquared = radiusSquared;
        this.cosFov = cosFov;
        this.fovEnabled = fovEnabled;
        this.lineOfSight = lineOfSight;
    }

    /** A gate that shows when the viewer is in the same world and within {@code radius} blocks. */
    public static VisibilityGate range(double radius) {
        return new VisibilityGate(squareRadius(radius), -1.0, false, null);
    }

    /**
     * A gate that also requires the hologram to fall inside the viewer's field-of-view cone, where
     * {@code fovDegrees} is the full cone angle (NPCLib's default is 60). A wider angle shows it from a
     * larger arc of head directions; {@code 360} would disable the cone.
     */
    public static VisibilityGate rangeAndFov(double radius, double fovDegrees) {
        return new VisibilityGate(squareRadius(radius), cosOfFullAngle(fovDegrees), true, null);
    }

    /**
     * A gate that also requires a clear sightline: after the range cull passes, {@code hasLineOfSight} is
     * consulted with the eye and hologram locations and must return {@code true} for the hologram to show.
     * The caller owns the ray-trace (run on the region thread); this keeps the predicate pure and testable.
     */
    public static VisibilityGate rangeAndLineOfSight(double radius, BiPredicate<Location, Location> hasLineOfSight) {
        Objects.requireNonNull(hasLineOfSight, "hasLineOfSight");
        return new VisibilityGate(squareRadius(radius), -1.0, false, hasLineOfSight);
    }

    /**
     * A gate that requires the hologram to be in range, inside the FOV cone, and on a clear sightline. The
     * checks run cheapest-first: range, then the cone, then the injected {@code hasLineOfSight} ray-trace.
     */
    public static VisibilityGate rangeFovAndLineOfSight(
            double radius, double fovDegrees, BiPredicate<Location, Location> hasLineOfSight) {
        Objects.requireNonNull(hasLineOfSight, "hasLineOfSight");
        return new VisibilityGate(squareRadius(radius), cosOfFullAngle(fovDegrees), true, hasLineOfSight);
    }

    private static double cosOfFullAngle(double fovDegrees) {
        if (!(fovDegrees > 0) || !Double.isFinite(fovDegrees)) {
            throw new IllegalArgumentException("fovDegrees must be a positive, finite angle");
        }
        return Math.cos(Math.toRadians(fovDegrees) / 2.0);
    }

    private static double squareRadius(double radius) {
        if (!(radius > 0) || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("radius must be a positive, finite number of blocks");
        }
        return radius * radius;
    }

    /**
     * Whether the viewer whose eye is at {@code eye} (its {@link Location#getDirection()} is the look vector)
     * should see a hologram at {@code hologram}. The world check short-circuits before
     * {@link Location#distanceSquared}, which throws across worlds; then the range cull; then, if enabled,
     * the FOV cone; then, if enabled, the injected line-of-sight check. A zero-length offset (viewer on top
     * of the hologram) is treated as inside the cone.
     */
    public boolean shouldShow(Location eye, Location hologram) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(hologram, "hologram");
        if (!sameWorldWithinRange(eye, hologram)) {
            return false;
        }
        if (fovEnabled && !withinCone(eye, hologram)) {
            return false;
        }
        return lineOfSight == null || lineOfSight.test(eye, hologram);
    }

    private boolean sameWorldWithinRange(Location eye, Location hologram) {
        World eyeWorld = eye.getWorld();
        World hologramWorld = hologram.getWorld();
        if (eyeWorld == null || hologramWorld == null || !eyeWorld.equals(hologramWorld)) {
            return false;
        }
        return eye.distanceSquared(hologram) <= radiusSquared;
    }

    private boolean withinCone(Location eye, Location hologram) {
        Vector toHologram = hologram.toVector().subtract(eye.toVector());
        double lengthSquared = toHologram.lengthSquared();
        if (lengthSquared < 1.0e-8) {
            return true;
        }
        Vector look = eye.getDirection();
        double dot = look.dot(toHologram) / Math.sqrt(lengthSquared * look.lengthSquared());
        return dot >= cosFov;
    }
}

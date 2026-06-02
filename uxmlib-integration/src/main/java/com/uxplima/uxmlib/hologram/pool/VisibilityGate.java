package com.uxplima.uxmlib.hologram.pool;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * The single pure check that decides whether one viewer should currently see a hologram. Centralizing it
 * here keeps the rule in one place: a {@link ViewerLifecycle} reconciles intent against exactly this gate,
 * and {@link HologramPool} can use the range-only form too. A gate combines a same-world range cull with an
 * optional field-of-view cone so a hologram only shows when the viewer is within reach and looking at it.
 *
 * <p>The cone test is the dot-product technique from NPCLib ({@code NPCBase#inViewOf}, MIT): the cosine of
 * the angle between the viewer's eye direction and the direction to the hologram equals the dot of those two
 * unit vectors, so {@code dot >= cos(halfAngle)} is a branch-free "is it inside the cone" test.
 */
public final class VisibilityGate {

    private final double radiusSquared;
    private final double cosFov;
    private final boolean fovEnabled;

    private VisibilityGate(double radiusSquared, double cosFov, boolean fovEnabled) {
        this.radiusSquared = radiusSquared;
        this.cosFov = cosFov;
        this.fovEnabled = fovEnabled;
    }

    /** A gate that shows when the viewer is in the same world and within {@code radius} blocks. */
    public static VisibilityGate range(double radius) {
        return new VisibilityGate(squareRadius(radius), -1.0, false);
    }

    /**
     * A gate that also requires the hologram to fall inside the viewer's field-of-view cone, where
     * {@code fovDegrees} is the full cone angle (NPCLib's default is 60). A wider angle shows it from a
     * larger arc of head directions; {@code 360} would disable the cone.
     */
    public static VisibilityGate rangeAndFov(double radius, double fovDegrees) {
        if (!(fovDegrees > 0) || !Double.isFinite(fovDegrees)) {
            throw new IllegalArgumentException("fovDegrees must be a positive, finite angle");
        }
        double halfAngle = Math.toRadians(fovDegrees) / 2.0;
        return new VisibilityGate(squareRadius(radius), Math.cos(halfAngle), true);
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
     * the FOV cone. A zero-length offset (viewer on top of the hologram) is treated as inside the cone.
     */
    public boolean shouldShow(Location eye, Location hologram) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(hologram, "hologram");
        if (!sameWorldWithinRange(eye, hologram)) {
            return false;
        }
        return !fovEnabled || withinCone(eye, hologram);
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

package com.uxplima.uxmlib.nametag;

import java.util.Objects;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The default {@link LineOfSight}: a thin wrapper over Bukkit's own eye-to-target visibility check. Paper's
 * {@link Player#hasLineOfSight(Entity)} already ray-traces from the viewer's eye to the target through solid
 * blocks (ignoring fluids and the target's own collision), which is exactly the test the fade wants — so there
 * is no reason to reimplement the trace. The renderer caps distance separately via the appearance view range,
 * which leaves this purely a "is something solid in the way" answer.
 */
public final class BlockLineOfSight implements LineOfSight {

    @Override
    public boolean obstructed(Player viewer, Entity target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        return !viewer.hasLineOfSight(target);
    }
}

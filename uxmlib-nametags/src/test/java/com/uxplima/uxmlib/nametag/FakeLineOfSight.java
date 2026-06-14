package com.uxplima.uxmlib.nametag;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * A scripted {@link LineOfSight}: the set of viewer UUIDs whose view is declared blocked. Lets a test fade one
 * viewer's nametag while leaving another's clear, with no world or real ray-trace involved.
 */
final class FakeLineOfSight implements LineOfSight {

    private final Set<UUID> obstructed = new HashSet<>();

    /** Mark {@code viewer} as having a blocked view of the target. */
    FakeLineOfSight obstruct(UUID viewer) {
        obstructed.add(viewer);
        return this;
    }

    @Override
    public boolean obstructed(Player viewer, Entity target) {
        return obstructed.contains(viewer.getUniqueId());
    }
}

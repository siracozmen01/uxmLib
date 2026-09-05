package com.uxplima.uxmlib.packet;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

/**
 * Allocates fake-entity ids from the shared server counter. Ids it hands out never collide with a real entity
 * because they come from the same monotonic source the server itself uses for spawning.
 */
public final class EntityIds {

    private EntityIds() {}

    /**
     * The next free entity id, taken from the first loaded world. The counter behind it is shared by every
     * world on the server, so an id allocated here is unique server-wide no matter which world asked for it.
     */
    public static int next() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            throw new IllegalStateException("cannot allocate an entity id before any world has loaded");
        }
        return next(worlds.get(0));
    }

    /**
     * The next free entity id, asked of {@code world}. Prefer this over {@link #next()} when the caller already
     * knows the world the fake entity will be shown in: on lines that track ids per level, the server skips ids
     * that world is already using. Where the counter is one static shared by every world, the argument makes no
     * difference to the id handed back: see {@link ServerInternals#nextEntityId}.
     */
    public static int next(World world) {
        Objects.requireNonNull(world, "world");
        return ServerInternals.nextEntityId(((CraftWorld) world).getHandle());
    }
}

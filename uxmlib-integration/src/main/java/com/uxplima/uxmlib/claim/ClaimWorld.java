package com.uxplima.uxmlib.claim;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.World;

import org.jspecify.annotations.NullMarked;

/**
 * The world a claim lookup is asked about, held by identity rather than by a live {@link World} handle.
 *
 * <p>A lookup runs against whatever the claim plugin holds in memory, and it may run long after the caller
 * read the location, so passing a {@code World} would pin an object across a reload. The persistent
 * {@link #uid()} survives that, and {@link #name()} is what an operator reads in a log line.
 *
 * <p>Equality is on the uid alone: renaming a world folder does not change which world it is.
 *
 * @param uid the world's persistent unique identifier
 * @param name the operator-facing world name
 */
@NullMarked
public record ClaimWorld(UUID uid, String name) {

    /** Canonical constructor null-checks both components. */
    public ClaimWorld {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(name, "name");
    }

    /** The reference to a live Bukkit world, which is how a caller with a {@code Location} in hand builds one. */
    public static ClaimWorld of(World world) {
        Objects.requireNonNull(world, "world");
        return new ClaimWorld(world.getUID(), world.getName());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ClaimWorld ref && uid.equals(ref.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }
}

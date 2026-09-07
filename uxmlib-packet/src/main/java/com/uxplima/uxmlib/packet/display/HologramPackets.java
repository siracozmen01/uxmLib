package com.uxplima.uxmlib.packet.display;

import java.util.List;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.joml.Vector3f;

/**
 * The seam between {@link PacketHologram} and the NMS packet construction. Every packet crosses this boundary
 * as an opaque {@link Object}, so this interface, and everything above it, carries no {@code net.minecraft}
 * reference and unit-tests against a fake. The implementation that builds the real Mojang-mapped packets is
 * {@code display.internal.NmsHologramPackets}.
 *
 * <p>{@link DisplayTextPackets} is the neighbour that overrides the text of an entity the server already
 * spawned. This one is the other half the library was missing: it spawns the display in the client and never
 * on the server, so the hologram costs no entity, survives a chunk unload and cannot be hit, moved or
 * duplicated by anything on the server.
 */
public interface HologramPackets {

    /**
     * Reserve a fresh entity id for a display that exists only in a client. It must not collide with a live
     * server entity, so it comes from the server's own counter.
     */
    int allocateEntityId();

    /** Build the add-entity packet that spawns a text display with {@code entityId} at the given position. */
    Object spawnPacket(int entityId, double x, double y, double z);

    /**
     * Build the set-entity-data packet that paints {@code entityId} with one line of text, the shared
     * {@link HologramAppearance}, and that line's own offset within the stack.
     */
    Object metadataPacket(int entityId, Component text, HologramAppearance appearance, Vector3f translation);

    /** Build the remove-entities packet that despawns {@code entityIds} for one viewer. */
    Object removePacket(int[] entityIds);

    /** Wrap several packets into one bundle so the client applies them in a single frame. */
    Object bundle(List<Object> packets);

    /** Write {@code packet} to {@code viewer}'s connection. A no-op if the connection cannot be resolved. */
    void send(Player viewer, Object packet);
}

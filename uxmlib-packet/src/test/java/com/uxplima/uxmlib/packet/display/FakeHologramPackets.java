package com.uxplima.uxmlib.packet.display;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.joml.Vector3f;

/**
 * A recording fake of {@link HologramPackets}. Every packet is a small sentinel record, so a test can assert
 * the structure of what was built and, through {@link #sends}, which viewer each packet reached. Entity ids
 * come from a counter so a test can check that an id stays bound to its line across refreshes.
 */
final class FakeHologramPackets implements HologramPackets {

    /** A packet that adds an entity at a position. */
    record Spawn(int entityId, double x, double y, double z) {}

    /** A packet that paints one line; carries the text so a test can match viewer to content. */
    record Metadata(int entityId, Component text, HologramAppearance appearance, Vector3f translation) {}

    /** A packet that removes entities. */
    record Remove(List<Integer> entityIds) {}

    /** A bundle wrapping several packets into one frame. */
    record Bundle(List<Object> packets) {}

    /** One recorded send: the packet and the viewer it was written to. */
    record Sent(Player viewer, Object packet) {}

    private final AtomicInteger nextId = new AtomicInteger(1);
    final List<Sent> sends = new ArrayList<>();

    @Override
    public int allocateEntityId() {
        return nextId.getAndIncrement();
    }

    @Override
    public Object spawnPacket(int entityId, double x, double y, double z) {
        return new Spawn(entityId, x, y, z);
    }

    @Override
    public Object metadataPacket(int entityId, Component text, HologramAppearance appearance, Vector3f translation) {
        return new Metadata(entityId, text, appearance, new Vector3f(translation));
    }

    @Override
    public Object removePacket(int[] entityIds) {
        List<Integer> ids = new ArrayList<>(entityIds.length);
        for (int id : entityIds) {
            ids.add(id);
        }
        return new Remove(ids);
    }

    @Override
    public Object bundle(List<Object> packets) {
        return new Bundle(List.copyOf(packets));
    }

    @Override
    public void send(Player viewer, Object packet) {
        sends.add(new Sent(viewer, packet));
    }

    /** How many entity ids have been handed out so far. */
    int allocations() {
        return nextId.get() - 1;
    }

    /** Everything written to {@code viewer}, in order. */
    List<Object> packetsFor(Player viewer) {
        return sends.stream()
                .filter(sent -> sent.viewer().equals(viewer))
                .map(Sent::packet)
                .toList();
    }

    void forget() {
        sends.clear();
    }
}

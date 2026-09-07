package com.uxplima.uxmlib.packet.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * An {@link ItemPackets} whose packets are a sentinel record rather than a server packet, so the listener's
 * whole decision can be driven without a Mojang-mapped server on the classpath. It applies the view to the
 * item it carries and rebuilds itself only when the view changed something, which is the contract the real
 * Mojang-mapped implementation keeps.
 */
final class FakeItemPackets implements ItemPackets {

    /** A stand-in for a clientbound packet that carries one item. */
    record Slot(ItemStack item) {}

    /** Every packet the port was asked to rewrite, in order. */
    private final List<Object> asked = new ArrayList<>();

    @Override
    public boolean carriesItems(Object packet) {
        return packet instanceof Slot;
    }

    @Override
    public @Nullable Object withItems(Object packet, UnaryOperator<ItemStack> shown) {
        asked.add(packet);
        Slot slot = (Slot) packet;
        ItemStack view = shown.apply(slot.item());
        return view.equals(slot.item()) ? null : new Slot(view);
    }

    List<Object> asked() {
        return List.copyOf(asked);
    }
}

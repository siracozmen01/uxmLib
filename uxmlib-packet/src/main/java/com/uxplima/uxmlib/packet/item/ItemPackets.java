package com.uxplima.uxmlib.packet.item;

import java.util.function.UnaryOperator;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * Reads the items out of a clientbound packet and builds the same packet again around different ones.
 *
 * <p>The port exists so that everything above it stays free of {@code net.minecraft}: a packet crosses this
 * boundary as an opaque {@link Object}, exactly as it does for the hologram and npc ports. The single
 * Mojang-mapped implementation lives behind it in {@code item.internal}.
 */
public interface ItemPackets {

    /**
     * Whether {@code packet} carries at least one item this port can rewrite. Cheap: a type test, so the
     * interceptor can skip the overwhelming majority of outbound traffic without decoding anything.
     */
    boolean carriesItems(Object packet);

    /**
     * The same packet built again with each item it carries replaced by {@code shown}.
     *
     * <p>{@code shown} is called once per non-empty item. Returning the argument, or anything equal to it,
     * counts as no change. This returns {@code null} when nothing changed at all, so the caller forwards the
     * original packet and no copy is made for the common case of an item nobody wanted to touch.
     *
     * @return the replacement packet, or {@code null} when every item was left alone
     */
    @Nullable Object withItems(Object packet, UnaryOperator<ItemStack> shown);
}

package com.uxplima.uxmlib.packet.item;

import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/**
 * What one viewer is shown in place of an item the server holds.
 *
 * <p>This is the whole seam a plugin implements. It is handed the item as the server stores it and hands back
 * the item to draw in that client, for that viewer alone. Hand back the argument, or anything equal to it, to
 * leave the item alone: the pipeline then forwards the original packet and nothing is allocated.
 *
 * <p>The item passed in is already a copy, so writing to it changes nothing on the server. Nothing here can
 * reach the real stack, which is the property the mechanism exists to give: a plugin decorates what a player
 * reads without ever editing what a chest holds.
 *
 * <p><b>This runs on a Netty I/O thread.</b> Read a snapshot, do arithmetic and string work, and return. Do
 * not touch a world, an entity or an inventory here, and do not block. An implementation that throws is
 * caught by the listener registry and the original packet passes, so a fault is visible in the log and never
 * on the connection.
 */
@FunctionalInterface
public interface ItemView {

    /**
     * The item {@code viewer} is shown in place of {@code real}.
     *
     * @param viewer the player the packet is on its way to
     * @param real a copy of the item as the server holds it; never {@code null}, never empty
     * @return the item to draw, or {@code real} to leave it alone; never {@code null}
     */
    ItemStack shownTo(UUID viewer, ItemStack real);
}

/**
 * The item a client is shown, which is not always the item the server holds.
 *
 * <p>A plugin that wants to write something onto an item a player reads has two ways to do it. It can write
 * the lore onto the real stack and take it off again, which is what this market does and which loses a
 * player's item the day two plugins do it at once or a save lands between the two writes. Or it can leave the
 * stack alone and change the copy on its way to the client, which is what this package is for.
 *
 * <p>Three types and one installer:
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.packet.item.ItemView}: the seam a plugin implements. Given a viewer and a
 *       copy of the item, hand back what that viewer should read.
 *   <li>{@link com.uxplima.uxmlib.packet.item.ItemPackets}: which clientbound packets carry an item, and how
 *       to build one again around different items. The only part that names the server internals.
 *   <li>{@link com.uxplima.uxmlib.packet.item.ItemViewListener}: the outbound listener that puts the view in
 *       front of every item, and rewrites the packet only when the view actually changed one.
 *   <li>{@link com.uxplima.uxmlib.packet.item.ItemViews}: the installer, which is the pipeline choreography a
 *       consumer would otherwise write again: inject on join, eject on quit, reorder after a delay.
 * </ul>
 *
 * <p>This package holds a mechanism and no rule of play. It knows nothing of lore formats, rarity, glint,
 * enchantment vocabulary or a tooltip layout: it moves an item through a function on its way out. What that
 * function writes is the plugin's, always.
 */
@NullMarked
package com.uxplima.uxmlib.packet.item;

import org.jspecify.annotations.NullMarked;

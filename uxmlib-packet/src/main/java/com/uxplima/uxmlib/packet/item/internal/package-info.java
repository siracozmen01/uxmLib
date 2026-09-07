/**
 * The Mojang-mapped half of the item view: which clientbound packets carry an item, how to read the items out
 * and how to build the same packet again around different ones. NMS is quarantined here, behind
 * {@link com.uxplima.uxmlib.packet.item.ItemPackets}, the way the display, npc and tablist layers quarantine
 * theirs.
 */
@NullMarked
package com.uxplima.uxmlib.packet.item.internal;

import org.jspecify.annotations.NullMarked;

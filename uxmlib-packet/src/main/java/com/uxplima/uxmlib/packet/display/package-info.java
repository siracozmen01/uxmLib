/**
 * Text displays driven by packets, in two shapes.
 *
 * <p>{@link com.uxplima.uxmlib.packet.display.DisplayTextPackets} is the per-viewer text override for a display
 * the server already spawned: one real shared entity, plus a metadata packet that gives one viewer their own
 * text. It is the FancyHolograms approach to a per-viewer hologram, done without a packet-entity rewrite.
 *
 * <p>{@link com.uxplima.uxmlib.packet.display.PacketHologram} is the other half: a hologram that is sent and
 * never spawned. It builds the display in the viewer's client alone, so the server holds no entity, the text
 * cannot be hit, moved, duplicated or swept, and a chunk unload takes nothing away. It renders by distance and
 * writes no packet at all while nobody is near. Use it for decoration and use
 * {@code Holograms.spawn} when an operator has to be able to walk into the hologram and click it.
 *
 * <p>Everything here is NMS-free; the two Mojang-mapped implementations sit in {@code display.internal}.
 */
@NullMarked
package com.uxplima.uxmlib.packet.display;

import org.jspecify.annotations.NullMarked;

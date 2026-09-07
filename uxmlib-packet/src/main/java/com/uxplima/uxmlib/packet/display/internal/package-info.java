/**
 * The Mojang-mapped implementations of the two display packet ports: the per-viewer text override, and the
 * packet hologram that spawns a display in a client and never on the server. NMS is quarantined here, behind
 * {@link com.uxplima.uxmlib.packet.display.DisplayTextPackets} and
 * {@link com.uxplima.uxmlib.packet.display.HologramPackets}, exactly as the nametag and tab-list layers
 * quarantine theirs.
 */
@NullMarked
package com.uxplima.uxmlib.packet.display.internal;

import org.jspecify.annotations.NullMarked;

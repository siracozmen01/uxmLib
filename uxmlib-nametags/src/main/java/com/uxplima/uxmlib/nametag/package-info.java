/**
 * <b>EXPERIMENTAL: packet nametag renderer.</b> A from-scratch, MIT-clean per-viewer nametag layer for
 * Paper 1.21+. It renders a target's name to each viewer through scoreboard-team and metadata packets so
 * different viewers can see different prefixes, suffixes, colours, or visibility: without touching the
 * server-side scoreboard. PacketEvents (the off-the-shelf choice) is GPL, so none of it is borrowed; the
 * packets are constructed against the Mojang-mapped dev bundle and quarantined to a single NMS class, while
 * the channel/send plumbing is reused from {@code uxmlib-pipeline}.
 *
 * <p>This package holds the pure value types ({@link com.uxplima.uxmlib.nametag.Appearance},
 * {@link com.uxplima.uxmlib.nametag.PerViewerText}, {@link com.uxplima.uxmlib.nametag.Billboard},
 * {@link com.uxplima.uxmlib.nametag.Alignment}), the {@link com.uxplima.uxmlib.nametag.NametagPackets} port
 * (the NMS seam), and the {@link com.uxplima.uxmlib.nametag.NametagRenderer} that drives a single per-viewer
 * line. The NMS packet builder behind the port lands in a later milestone. Treat every type here as unstable
 * API.
 */
@NullMarked
package com.uxplima.uxmlib.nametag;

import org.jspecify.annotations.NullMarked;

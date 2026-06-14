/**
 * <b>EXPERIMENTAL — packet nametag renderer.</b> A from-scratch, MIT-clean per-viewer nametag layer for
 * Paper 1.21+. It renders a target's name to each viewer through scoreboard-team and metadata packets so
 * different viewers can see different prefixes, suffixes, colours, or visibility — without touching the
 * server-side scoreboard. PacketEvents (the off-the-shelf choice) is GPL, so none of it is borrowed; the
 * packets are constructed against the Mojang-mapped dev bundle and quarantined to a single NMS class, while
 * the channel/send plumbing is reused from {@code uxmlib-npc}.
 *
 * <p>This package currently holds only the build wiring and is otherwise empty; the port, the renderer
 * logic, and the NMS packet builder land in later milestones. Treat every type here as unstable API.
 */
@NullMarked
package com.uxplima.uxmlib.nametag;

import org.jspecify.annotations.NullMarked;

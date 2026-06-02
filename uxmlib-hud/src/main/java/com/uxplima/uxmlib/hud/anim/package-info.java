/**
 * Stateful text animators that produce a fresh {@link net.kyori.adventure.text.Component} each tick — the
 * per-character colour sweep and scrolling ticker MiniMessage cannot express statically. Each animator is a
 * pure state machine: {@code frame()} reads the current frame and {@code advance()} steps to the next one, so
 * a caller drives it off the shared {@code Scheduler} tick and the sequence is unit-tested by hand without
 * any Bukkit in play. Nothing here touches Paper.
 */
@NullMarked
package com.uxplima.uxmlib.hud.anim;

import org.jspecify.annotations.NullMarked;

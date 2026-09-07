/**
 * One name per player, composed from every plugin that wants a say in it. A player may belong to exactly one
 * scoreboard team, so plugins that each create their own teams fight over that single slot and the last one
 * to write wins: silently. {@link com.uxplima.uxmlib.hud.nametag.NametagRegistry} ends the fight by owning
 * the team itself: each plugin hands it a {@link com.uxplima.uxmlib.hud.nametag.NametagContribution} and the
 * registry composes them into the one name the player wears, so a prefix, a suffix and a colour from three
 * different plugins coexist instead of overwriting each other.
 *
 * <p>{@link com.uxplima.uxmlib.hud.nametag.ComposedNametag} is the composition itself and is pure: order,
 * separator and colour precedence are decided with no server in sight. Applying it is a
 * {@link com.uxplima.uxmlib.hud.nametag.NametagSink}, whose shipped implementation writes to a scoreboard
 * team; a consumer can supply another.
 *
 * <p>The registry settles the fight inside one jar. Every plugin of ours relocates its own copy of this
 * package, so two plugins each building a registry is two registries and two teams, and the second still
 * loses. {@link com.uxplima.uxmlib.hud.nametag.SharedNametags} is what makes it one registry for one server:
 * the first plugin to load builds it and offers it through the server's service manager under a boot-loader
 * type, and every plugin after that is handed a view onto it. Take
 * {@link com.uxplima.uxmlib.hud.nametag.Nametags} in your own wiring and let
 * {@link com.uxplima.uxmlib.hud.nametag.SharedNametags#claim} decide which of the two you are holding.
 */
@NullMarked
package com.uxplima.uxmlib.hud.nametag;

import org.jspecify.annotations.NullMarked;

/**
 * The shared packet foundation: the small generic helpers that build and adapt Mojang-mapped server packets,
 * factored out of the nametag renderer once a second feature (the tablist renderer) needed the same mechanics.
 *
 * <p>Each helper is a {@code final} class of static methods that quarantines a single piece of unavoidable
 * {@code net.minecraft} reach so that the rest of every consuming module stays pure and unit-testable against a
 * fake. This follows the same precedent as {@code uxmlib-pipeline}'s {@code ChannelResolver}.
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.packet.Components}: Adventure {@code Component} to the vanilla component.
 *   <li>{@link com.uxplima.uxmlib.packet.Bundles}: wrap several clientbound game packets into one bundle.
 *   <li>{@link com.uxplima.uxmlib.packet.Reflect}: read a server static field (e.g. an {@code
 *       EntityDataAccessor}) by its Mojang-mapped name, failing loudly on a mapping mismatch.
 *   <li>{@link com.uxplima.uxmlib.packet.Codecs}: build a packet that exposes only a private buffer
 *       constructor by writing its wire form and decoding it through the public stream codec.
 *   <li>{@link com.uxplima.uxmlib.packet.EntityIds}: allocate a fake-entity id from the shared server counter.
 * </ul>
 *
 * <p>Beside the helpers sit the feature packages built on them: {@code display}, {@code npc}, {@code
 * scoreboard}, {@code tablist}, and {@code item}, which is the one that rewrites an item on its way to a
 * client while leaving the item the server holds exactly as it was.
 *
 * <p>Built against the Mojang-mapped 1.21.11 dev bundle; Paper's runtime remapper maps these back to the
 * server's own mappings at load.
 */
@NullMarked
package com.uxplima.uxmlib.packet;

import org.jspecify.annotations.NullMarked;

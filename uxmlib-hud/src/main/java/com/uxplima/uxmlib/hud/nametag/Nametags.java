package com.uxplima.uxmlib.hud.nametag;

import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * What a plugin holds to have a say in a player's name.
 *
 * <p>{@link NametagRegistry} is the implementation that owns the composition and the scoreboard team.
 * {@link SharedNametags} is the other one: it forwards to whichever plugin on this server owns the registry,
 * so two plugins that each relocated their own copy of uxmLib still compose into one name.
 *
 * <p>Take this type rather than the class in anything a consumer wires, and let
 * {@link SharedNametags#claim} decide which of the two it gets.
 */
public interface Nametags {

    /** Record {@code contribution} for {@code player}, replacing that plugin's previous one, and recompose. */
    void contribute(Player player, NametagContribution contribution);

    /** Take back what {@code plugin} contributed to {@code player} alone, and recompose that name. */
    void withdraw(Player player, String plugin);

    /** Take back everything {@code plugin} contributed, for a plugin disabling, and recompose every name. */
    void withdraw(String plugin);

    /** Forget a player entirely and drop the name they wore; for a quit. */
    void forget(Player player);

    /** Forget the player with {@code id}, as {@link #forget(Player)} does when the Player is already gone. */
    void forget(UUID id);

    /** What {@code id} currently wears, for a consumer that wants to inspect the composition it caused. */
    ComposedNametag composed(UUID id);

    /** Give the server back what this plugin put on it. */
    void close();
}

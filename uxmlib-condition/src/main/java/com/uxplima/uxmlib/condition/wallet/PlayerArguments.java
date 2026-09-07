package com.uxplima.uxmlib.condition.wallet;

import org.bukkit.entity.Player;

/**
 * What an economy plugin wants where the player goes.
 *
 * <p>Three answers are in use: the id, the name, and an offline player. A {@link Player} is already an
 * offline player and carries both of the others, so the shipped implementation asks the server for
 * nothing. It stays a seam so that a test can watch what the reader passed, and so that the reader itself
 * names no Bukkit type in its own logic.
 */
public interface PlayerArguments {

    /** {@code player} in the shape {@code shape} asks for. */
    Object of(EconomyBinding.Argument shape, Player player);

    /** The player itself, its id, or its name. A player with no name is passed its id instead. */
    static PlayerArguments ofPlayer() {
        return (shape, player) -> switch (shape) {
            case PLAYER_ID -> player.getUniqueId();
            case PLAYER_NAME -> player.getName();
            case OFFLINE_PLAYER -> player;
        };
    }
}

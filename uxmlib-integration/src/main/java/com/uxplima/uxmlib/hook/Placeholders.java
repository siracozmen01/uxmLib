package com.uxplima.uxmlib.hook;

import java.util.Objects;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * A present-guarded bridge to PlaceholderAPI. {@link #apply(Player, String)} expands {@code %papi%}
 * placeholders when PlaceholderAPI is installed and returns the text unchanged when it is not, so
 * callers can use it unconditionally. The {@code me.clip} classes are only touched inside the guarded
 * branch, so a server without PlaceholderAPI never resolves them.
 */
public final class Placeholders {

    /** The PlaceholderAPI plugin name, used for the presence guard. */
    public static final String PLUGIN = "PlaceholderAPI";

    private Placeholders() {}

    /** Whether PlaceholderAPI is installed and enabled. */
    public static boolean isAvailable() {
        return Hooks.isPresent(PLUGIN);
    }

    /** Expand placeholders in {@code text} for {@code player}; returns {@code text} unchanged if absent. */
    public static String apply(Player player, String text) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(text, "text");
        if (!isAvailable()) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}

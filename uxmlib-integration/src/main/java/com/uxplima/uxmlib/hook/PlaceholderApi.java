package com.uxplima.uxmlib.hook;

import java.util.Objects;

import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * A present-guarded bridge to PlaceholderAPI. {@link #apply(Player, String)} expands {@code %papi%}
 * placeholders when PlaceholderAPI is installed and returns the text unchanged when it is not, so
 * callers can use it unconditionally. The {@code me.clip} classes are only touched inside the guarded
 * branch, so a server without PlaceholderAPI never resolves them.
 *
 * <p>Named after the plugin it bridges, not after the idea. {@code text.Placeholders} is the general one: a
 * typed, lazy placeholder layer over any subject, needing no plugin at all. This module depends on that one,
 * so both are in scope in the same file, and the specific of the two is the one that takes the specific name.
 */
public final class PlaceholderApi {

    /** The PlaceholderAPI plugin name, used for the presence guard. */
    public static final String PLUGIN = "PlaceholderAPI";

    private PlaceholderApi() {}

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

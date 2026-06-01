package com.uxplima.uxmlib.hologram;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;

/**
 * Stamps every hologram entity with a persistent marker so it can be told apart from any other display
 * entity in the world. Because {@code TextDisplay}s persist, this lets {@link HologramManager} sweep up
 * holograms orphaned by a crash on the next startup. The key is created once, never on a hot path.
 */
final class Markers {

    private static final NamespacedKey KEY =
            Objects.requireNonNull(NamespacedKey.fromString("uxmlib:hologram"), "marker key");

    private Markers() {}

    /** Tag {@code display} as a uxmLib hologram. */
    static void stamp(Display display) {
        display.getPersistentDataContainer().set(KEY, PersistentDataType.BOOLEAN, true);
    }

    /** Whether {@code display} carries the uxmLib hologram marker. */
    static boolean isHologram(Display display) {
        return display.getPersistentDataContainer().has(KEY, PersistentDataType.BOOLEAN);
    }
}

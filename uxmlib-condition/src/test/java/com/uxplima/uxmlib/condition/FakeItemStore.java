package com.uxplima.uxmlib.condition;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * An item store held in a map, so the item condition and the {@code [take-item]} action can be driven without
 * a server. Like the real store it refuses a take it cannot cover in full, and it records how many takes it
 * applied so a test can prove that a refusal consumed nothing.
 */
public final class FakeItemStore implements ItemStore {

    private final Map<String, Integer> held = new HashMap<>();
    private int takes;

    public FakeItemStore(String item, int amount) {
        held.put(key(item), amount);
    }

    @Override
    public int count(@Nullable Player player, String item) {
        return held.getOrDefault(key(item), 0);
    }

    @Override
    public boolean take(@Nullable Player player, String item, int amount) {
        if (amount <= 0) {
            return true;
        }
        int have = count(player, item);
        if (have < amount) {
            return false;
        }
        takes++;
        held.put(key(item), have - amount);
        return true;
    }

    /** How many takes were actually applied; a refused take must leave this untouched. */
    public int takes() {
        return takes;
    }

    private static String key(String item) {
        return item.strip().toLowerCase(Locale.ROOT);
    }
}

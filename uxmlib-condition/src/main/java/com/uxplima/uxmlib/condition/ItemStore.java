package com.uxplima.uxmlib.condition;

import java.util.Objects;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * The seam through which an {@link ItemCondition} counts what a player holds and a {@code [take-item]} action
 * consumes it. It is the item mirror of {@link OperandResolver}: a plain contract this module owns.
 *
 * <p>The item is an opaque string. {@link #inventory()} reads it as a Bukkit material name, which is the
 * plain answer and the one most servers want; a plugin with its own custom-item vocabulary passes its own
 * implementation and the engine is none the wiser. This module fixes no item table, no rarity and no price.
 *
 * <p>Both methods take a nullable player because a request may carry a non-player actor. An implementation
 * answers zero and refuses the take in that case rather than throwing.
 */
public interface ItemStore {

    /**
     * How many of {@code item} the subject holds. A {@code null} player or an unrecognised item reads zero
     * rather than throwing.
     */
    int count(@Nullable Player player, String item);

    /**
     * Take {@code amount} of {@code item} from the subject and report whether the whole amount was taken.
     *
     * <p>An implementation must be all or nothing. Taking three and then failing on the fourth is the defect
     * this sentence exists to forbid: count the whole cost first, and only consume once it is covered. A
     * non-positive amount takes nothing and succeeds.
     */
    boolean take(@Nullable Player player, String item, int amount);

    /**
     * The empty store: every count reads zero and every take fails. It is the default on a request and on an
     * action context, so a {@code [take-item]} on an unwired engine fails loudly rather than silently
     * succeeding.
     */
    static ItemStore empty() {
        return new ItemStore() {

            @Override
            public int count(@Nullable Player player, String item) {
                Objects.requireNonNull(item, "item");
                return 0;
            }

            @Override
            public boolean take(@Nullable Player player, String item, int amount) {
                Objects.requireNonNull(item, "item");
                return amount <= 0;
            }
        };
    }

    /**
     * A store over the subject's own inventory, matching {@code item} against a Bukkit material name. This is
     * the plain default an operator expects when they write {@code [take-item] diamond 3}.
     *
     * <p>It reads and writes the player's storage contents, so it must run on the thread that owns the
     * player. The {@code [take-item]} action is declared sync for exactly that reason and a Folia-aware driver
     * routes it through the entity scheduler.
     */
    static ItemStore inventory() {
        return new InventoryItemStore();
    }
}

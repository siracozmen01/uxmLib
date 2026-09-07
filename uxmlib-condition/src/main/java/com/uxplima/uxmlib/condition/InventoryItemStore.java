package com.uxplima.uxmlib.condition;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * The {@link ItemStore#inventory()} implementation: counts and consumes plain Bukkit materials out of the
 * subject's storage contents.
 *
 * <p>The take is deliberately two passes. The first counts the whole cost across every matching stack and
 * refuses without touching anything when the total falls short; only then does the second pass consume. A
 * one-pass loop that decremented as it went would leave a player short of both the items and the reward when
 * the last stack came up small, which is the failure mode this class is written to make impossible.
 *
 * <p>Both methods read and write the player's inventory, so the caller must already be on the thread that
 * owns the player. That is why the {@code [take-item]} action reports itself sync.
 */
final class InventoryItemStore implements ItemStore {

    @Override
    public int count(@Nullable Player player, String item) {
        Objects.requireNonNull(item, "item");
        Material material = material(item);
        if (player == null || material == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Override
    public boolean take(@Nullable Player player, String item, int amount) {
        Objects.requireNonNull(item, "item");
        if (amount <= 0) {
            return true;
        }
        Material material = material(item);
        if (player == null || material == null) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (total(contents, material) < amount) {
            return false;
        }
        consume(contents, material, amount);
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private static int total(ItemStack[] contents, Material material) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (matches(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void consume(ItemStack[] contents, Material material, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!matches(stack, material)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            remaining -= taken;
            if (taken == stack.getAmount()) {
                contents[slot] = null;
            } else {
                // Clone rather than mutate: getStorageContents may hand back the live stack on some
                // inventory implementations, and a half-applied edit there would survive a later refusal.
                ItemStack reduced = stack.clone();
                reduced.setAmount(stack.getAmount() - taken);
                contents[slot] = reduced;
            }
        }
    }

    private static boolean matches(@Nullable ItemStack stack, Material material) {
        return stack != null && stack.getType() == material;
    }

    private static @Nullable Material material(String item) {
        return Material.matchMaterial(item.strip());
    }
}

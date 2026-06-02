package com.uxplima.uxmlib.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.item.GuiItem;

/**
 * Bridges a {@link SlotAnimation} to a live inventory. A slot is free for the overlay only when no
 * {@link GuiItem} owns it (it is not part of the menu's item map) and the inventory cell is empty, so the
 * moving highlight never paints over a button a caller placed. Lighting writes the highlight icon; clearing
 * empties the cell — and the animation only ever clears slots it lit itself.
 */
final class InventorySink implements SlotAnimation.Sink {

    private final Inventory inventory;
    private final Map<Integer, GuiItem> items;

    InventorySink(Inventory inventory, Map<Integer, GuiItem> items) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.items = Objects.requireNonNull(items, "items");
    }

    @Override
    public boolean isFree(int slot) {
        if (slot < 0 || slot >= inventory.getSize() || items.containsKey(slot)) {
            return false;
        }
        ItemStack current = inventory.getItem(slot);
        return current == null || current.getType().isAir();
    }

    @Override
    public boolean holdsIcon(int slot, ItemStack icon) {
        // A button placed through the menu lands in the item map; if that happened, the overlay no longer
        // owns the cell even if its rendered icon looks similar, so it must not touch it.
        if (slot < 0 || slot >= inventory.getSize() || items.containsKey(slot)) {
            return false;
        }
        ItemStack current = inventory.getItem(slot);
        return current != null && current.isSimilar(icon);
    }

    @Override
    public void light(int slot, ItemStack icon) {
        inventory.setItem(slot, icon);
    }

    @Override
    public void clear(int slot) {
        inventory.clear(slot);
    }
}

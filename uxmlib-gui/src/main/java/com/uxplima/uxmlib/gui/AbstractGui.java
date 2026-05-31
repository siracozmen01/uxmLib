package com.uxplima.uxmlib.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * Shared menu behaviour: holds the slot→item map, lazily builds the backing inventory (so {@code this}
 * never escapes a constructor), renders items into it, and routes clicks. Clicks inside the menu are
 * cancelled before the slot action runs, so an unconfigured menu cannot leak items.
 */
abstract class AbstractGui implements Gui {

    private final Component title;
    private final int size;
    private final Map<Integer, GuiItem> items = new HashMap<>();
    private @Nullable Inventory inventory;
    private @Nullable Consumer<InventoryCloseEvent> closeHandler;

    AbstractGui(Component title, int rows) {
        this.title = Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6");
        }
        this.size = rows * 9;
    }

    @Override
    public final Component title() {
        return title;
    }

    @Override
    public final int size() {
        return size;
    }

    @Override
    public void set(int slot, GuiItem item) {
        Objects.requireNonNull(item, "item");
        checkSlot(slot);
        items.put(slot, item);
        if (inventory != null) {
            inventory.setItem(slot, item.item());
        }
    }

    @Override
    public void remove(int slot) {
        checkSlot(slot);
        items.remove(slot);
        if (inventory != null) {
            inventory.clear(slot);
        }
    }

    @Override
    public void clear() {
        items.clear();
        if (inventory != null) {
            inventory.clear();
        }
    }

    @Override
    public void onClose(Consumer<InventoryCloseEvent> handler) {
        this.closeHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void open(HumanEntity viewer) {
        Objects.requireNonNull(viewer, "viewer");
        viewer.openInventory(getInventory());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Cancel by default so items can never be dragged out of an unconfigured menu.
        event.setCancelled(true);
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(inventory)) {
            return;
        }
        GuiItem item = items.get(event.getSlot());
        if (item != null) {
            item.action().accept(event);
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        Consumer<InventoryCloseEvent> handler = closeHandler;
        if (handler != null) {
            handler.accept(event);
        }
    }

    @Override
    public final Inventory getInventory() {
        Inventory inv = inventory;
        if (inv == null) {
            inv = Bukkit.createInventory(this, size, title);
            inventory = inv;
            render(inv);
        }
        return inv;
    }

    /** The item map, for subclasses that render derived content (e.g. pagination). */
    final Map<Integer, GuiItem> items() {
        return items;
    }

    /** The live inventory if it has been built, else {@code null}. */
    final @Nullable Inventory liveInventory() {
        return inventory;
    }

    private void render(Inventory inv) {
        for (Map.Entry<Integer, GuiItem> entry : items.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue().item());
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("slot must be 0.." + (size - 1));
        }
    }
}

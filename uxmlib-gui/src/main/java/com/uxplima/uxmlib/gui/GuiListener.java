package com.uxplima.uxmlib.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * The single Bukkit listener that drives every {@link Gui}. Because a menu is its inventory's holder,
 * each event is routed back to the owning menu via {@code getInventory().getHolder()}; events for
 * inventories the framework does not own are ignored. Registered once with {@link Guis#install}.
 */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.handleClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        // Dragging could deposit items into menu slots; cancel it outright for our menus.
        if (event.getInventory().getHolder() instanceof Gui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.handleClose(event);
        }
    }
}

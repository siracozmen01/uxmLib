package com.uxplima.uxmlib.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.kyori.adventure.text.Component;

/**
 * A chest-style menu backed by a Bukkit {@link Inventory} whose holder is the menu itself, so a single
 * registered {@link GuiListener} can route an event back to the menu that owns the inventory via
 * {@code inventory.getHolder()}.
 *
 * <p>Clicks are cancelled by default to stop items being pulled out; the slot's {@link GuiAction} then
 * runs and may re-allow the event. Build menus with {@link Guis} and remember to install the listener
 * once with {@link Guis#install}.
 */
public interface Gui extends InventoryHolder {

    /** The menu title. */
    Component title();

    /** The number of slots (rows × 9). */
    int size();

    /** Place {@code item} at {@code slot}; updates the open inventory immediately if it is showing. */
    void set(int slot, GuiItem item);

    /** Remove whatever is at {@code slot}. */
    void remove(int slot);

    /** Remove every item. */
    void clear();

    /** Open the menu for {@code viewer}. Must run on the viewer's region thread. */
    void open(HumanEntity viewer);

    /** Run when the menu closes. */
    void onClose(java.util.function.Consumer<InventoryCloseEvent> handler);

    /** Routes a click to the clicked slot's action. Called by {@link GuiListener}; not for direct use. */
    void handleClick(InventoryClickEvent event);

    /** Runs the close handler. Called by {@link GuiListener}; not for direct use. */
    void handleClose(InventoryCloseEvent event);

    /** The backing inventory. */
    @Override
    Inventory getInventory();
}

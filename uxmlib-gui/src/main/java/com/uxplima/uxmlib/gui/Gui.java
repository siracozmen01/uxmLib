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

    /**
     * Place {@code item} at 1-indexed {@code row} and {@code col} of a nine-wide chest grid. Convenience
     * over raw slot math; for non-chest {@link GuiType} menus use {@link #set(int, GuiItem)} instead.
     */
    void set(int row, int col, GuiItem item);

    /** Add each item to the next empty slot in order, stopping when the menu is full. */
    void addItem(GuiItem... items);

    /** The item at {@code slot}, or {@code null} if the slot is empty. */
    @org.jspecify.annotations.Nullable GuiItem getItem(int slot);

    /** A helper for filling borders, rows, columns, or the empty slots of this menu. */
    GuiFiller filler();

    /** Allow {@code modifier} (an interaction the menu would otherwise cancel). Returns this menu. */
    Gui allow(InteractionModifier modifier);

    /** Disallow {@code modifier} again (the default for every modifier). Returns this menu. */
    Gui disallow(InteractionModifier modifier);

    /** Whether {@code modifier} is currently allowed. */
    boolean allows(InteractionModifier modifier);

    /** Remove whatever is at {@code slot}. */
    void remove(int slot);

    /** Remove every item. */
    void clear();

    /** Open the menu for {@code viewer}. Must run on the viewer's region thread. */
    void open(HumanEntity viewer);

    /** Run when the menu closes. */
    void onClose(java.util.function.Consumer<InventoryCloseEvent> handler);

    /** Run when the menu opens. */
    void onOpen(java.util.function.Consumer<org.bukkit.event.inventory.InventoryOpenEvent> handler);

    /** Run when a menu slot with no item is clicked (a menu-wide fallback). */
    void onDefaultClick(java.util.function.Consumer<InventoryClickEvent> handler);

    /** Run when the click lands outside the inventory window entirely. */
    void onOutsideClick(java.util.function.Consumer<InventoryClickEvent> handler);

    /** Routes a click to the clicked slot's action. Called by {@link GuiListener}; not for direct use. */
    void handleClick(InventoryClickEvent event);

    /** Runs the close handler. Called by {@link GuiListener}; not for direct use. */
    void handleClose(InventoryCloseEvent event);

    /** Runs the open handler. Called by {@link GuiListener}; not for direct use. */
    void handleOpen(org.bukkit.event.inventory.InventoryOpenEvent event);

    /** The backing inventory. */
    @Override
    Inventory getInventory();
}

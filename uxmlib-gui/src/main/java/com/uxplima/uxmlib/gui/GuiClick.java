package com.uxplima.uxmlib.gui;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import org.jspecify.annotations.Nullable;

/**
 * Routes a click in a menu: cancels it unless the interaction class is allowed, then dispatches to the
 * clicked slot's item action, the empty-slot fallback, or the outside-click handler. Split out of
 * {@code AbstractGui} so the menu class stays small and click policy lives in one place.
 */
final class GuiClick {

    private GuiClick() {}

    /** Whether {@code event}'s action is one the menu has explicitly allowed (so it must not be cancelled). */
    static boolean isAllowed(Set<InteractionModifier> allowed, InventoryClickEvent event) {
        if (allowed.isEmpty()) {
            return false;
        }
        InteractionModifier modifier = InteractionModifier.forAction(event.getAction());
        return modifier != null && allowed.contains(modifier);
    }

    /** Apply the cancel policy and dispatch the click; returns true if a menu item slot was clicked. */
    static boolean route(
            Gui gui,
            @Nullable Inventory inventory,
            Map<Integer, GuiItem> items,
            Set<InteractionModifier> allowed,
            @Nullable Consumer<InventoryClickEvent> defaultClick,
            @Nullable Consumer<InventoryClickEvent> outsideClick,
            InventoryClickEvent event) {
        if (!isAllowed(allowed, event)) {
            event.setCancelled(true);
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            if (outsideClick != null) {
                outsideClick.accept(event);
            }
            return false;
        }
        if (!clicked.equals(inventory)) {
            return false;
        }
        GuiItem item = items.get(event.getSlot());
        if (item != null) {
            if (event.getWhoClicked() instanceof Player player) {
                item.action(new RenderContext(player, gui, event.getSlot())).accept(event);
            }
            return true;
        }
        if (defaultClick != null) {
            defaultClick.accept(event);
        }
        return false;
    }
}

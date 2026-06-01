package com.uxplima.uxmlib.gui;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.RenderContext;
import org.jspecify.annotations.Nullable;

/**
 * Routes a click in a menu: cancels it unless every interaction class it requires is allowed, then
 * dispatches to the clicked slot's item action, the empty-slot fallback, or the outside-click handler.
 * Split out of {@code AbstractGui} so the menu class stays small and click policy lives in one place.
 *
 * <p>Cancelling sets both {@link InventoryClickEvent#setCancelled(boolean)} and {@code setResult(DENY)};
 * the {@code DENY} result is the half that actually defeats some clients' optimistic item prediction, so
 * a denied take or drag does not briefly render on the client before the server corrects it. The cancel
 * policy is applied synchronously by {@link #applyPolicy}; the slot action dispatched by {@link #dispatch}
 * may be deferred to the next tick (so opening another inventory inside the handler is safe).
 */
final class GuiClick {

    private GuiClick() {}

    /** Whether {@code event}'s action is one the menu has explicitly allowed (so it must not be cancelled). */
    static boolean isAllowed(Set<InteractionModifier> allowed, InventoryClickEvent event) {
        Set<InteractionModifier> required = InteractionModifier.required(event);
        return required.isEmpty() || allowed.containsAll(required);
    }

    /** Cancel the event (with {@code DENY}) unless its interaction class is allowed. Must run in-event. */
    static void applyPolicy(Set<InteractionModifier> allowed, InventoryClickEvent event) {
        if (!isAllowed(allowed, event)) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    /** Dispatch the click to a slot action, the empty-slot fallback, or the outside handler. */
    static boolean dispatch(
            Gui gui,
            @Nullable Inventory inventory,
            Map<Integer, GuiItem> items,
            @Nullable Consumer<InventoryClickEvent> defaultClick,
            @Nullable Consumer<InventoryClickEvent> outsideClick,
            InventoryClickEvent event) {
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

    /**
     * Apply the drag policy: cancel only if a dragged raw slot lands in the menu's top inventory and
     * placing into the menu is not allowed. A drag that touches only the player's own inventory is left
     * alone, so a storage menu's drags into the player inventory still work.
     */
    static void routeDrag(Set<InteractionModifier> allowed, InventoryDragEvent event) {
        if (allowed.contains(InteractionModifier.ITEM_PLACE)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesMenu = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesMenu) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }
}

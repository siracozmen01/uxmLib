package com.uxplima.uxmlib.gui;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * A single class of inventory interaction a {@link Gui} can choose to allow. By default a menu cancels
 * every interaction, so its items cannot be taken, replaced, or dragged out. Allowing a modifier lets
 * that one class of interaction through — for instance a storage menu allows {@link #ITEM_TAKE} and
 * {@link #ITEM_PLACE} so a player can move real items in and out, while a button menu allows none.
 */
public enum InteractionModifier {

    /** Taking an item out of a menu slot. */
    ITEM_TAKE,

    /** Putting an item into a menu slot. */
    ITEM_PLACE,

    /** Swapping the cursor (or a hotbar slot) with a menu slot's item. */
    ITEM_SWAP,

    /** Dropping an item (Q / drop key) while the menu is open. */
    ITEM_DROP;

    // The action buckets mirror triumph-gui's reference classifier (MIT, dev.triumphteam.gui). A single
    // InventoryAction can belong to more than one bucket — a hotbar swap both takes from the slot and
    // swaps the cursor — and the top-vs-player-inventory direction decides take vs place for shift moves.
    // We re-implement the technique here rather than copy the listener.
    private static final Set<InventoryAction> TAKE_ACTIONS = EnumSet.of(
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ALL,
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.HOTBAR_SWAP);

    private static final Set<InventoryAction> PLACE_ACTIONS =
            EnumSet.of(InventoryAction.PLACE_ONE, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ALL);

    // HOTBAR_MOVE_AND_READD is deprecated-for-removal in the Paper API, so it is left out: the common
    // number-key swap surfaces as HOTBAR_SWAP, which is bucketed under both take and swap below.
    private static final Set<InventoryAction> SWAP_ACTIONS =
            EnumSet.of(InventoryAction.HOTBAR_SWAP, InventoryAction.SWAP_WITH_CURSOR);

    private static final Set<InventoryAction> DROP_ACTIONS = EnumSet.of(
            InventoryAction.DROP_ONE_SLOT,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.DROP_ALL_CURSOR);

    /**
     * The set of modifiers a click requires — i.e. those that must all be allowed for the click to pass.
     * An action can require several (a hotbar swap is both a take and a swap); a shift-move is a take when
     * it pulls out of the menu and a place when it pushes into it. Actions that touch only the player's own
     * inventory, and {@code NOTHING}/{@code CLONE_STACK}/{@code UNKNOWN}, require nothing.
     */
    static Set<InteractionModifier> required(InventoryClickEvent event) {
        EnumSet<InteractionModifier> required = EnumSet.noneOf(InteractionModifier.class);
        if (isTake(event)) {
            required.add(ITEM_TAKE);
        }
        if (isPlace(event)) {
            required.add(ITEM_PLACE);
        }
        if (isSwap(event)) {
            required.add(ITEM_SWAP);
        }
        if (isDrop(event)) {
            required.add(ITEM_DROP);
        }
        return required;
    }

    private static boolean isTake(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        if (touchesOnlyPlayer(event)) {
            return false;
        }
        // A shift-move out of the menu's top inventory pulls an item from the menu.
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return !clickedPlayerInventory(event);
        }
        return TAKE_ACTIONS.contains(action);
    }

    private static boolean isPlace(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        // A shift-move from the player's inventory pushes an item into the menu.
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return clickedPlayerInventory(event) && !topIsPlayer(event);
        }
        return PLACE_ACTIONS.contains(action) && !clickedPlayerInventory(event) && !topIsPlayer(event);
    }

    private static boolean isSwap(InventoryClickEvent event) {
        return SWAP_ACTIONS.contains(event.getAction()) && !touchesOnlyPlayer(event);
    }

    private static boolean isDrop(InventoryClickEvent event) {
        return DROP_ACTIONS.contains(event.getAction()) && !clickedPlayerInventory(event);
    }

    /** True when the click lands in the player's own inventory or the top inventory is itself the player. */
    private static boolean touchesOnlyPlayer(InventoryClickEvent event) {
        return clickedPlayerInventory(event) || topIsPlayer(event);
    }

    private static boolean clickedPlayerInventory(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        return clicked != null && clicked.getType() == InventoryType.PLAYER;
    }

    private static boolean topIsPlayer(InventoryClickEvent event) {
        return event.getInventory().getType() == InventoryType.PLAYER;
    }
}

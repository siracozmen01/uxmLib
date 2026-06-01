package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.item.GuiItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers the InteractionModifier allow/disallow behaviour on click cancellation. */
class GuiInteractionTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static InventoryView openView(Gui gui) {
        var player = MockBukkit.getMock().addPlayer();
        return java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
    }

    /** A click on a top-inventory (menu) slot. */
    private static InventoryClickEvent menuClick(Gui gui, int slot, InventoryAction action) {
        InventoryView view = openView(gui);
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, action);
    }

    /** A click on a bottom-inventory (player) slot, addressed by raw slot past the top inventory's size. */
    private static InventoryClickEvent playerClick(Gui gui, int playerSlot, InventoryAction action) {
        InventoryView view = openView(gui);
        int rawSlot = view.getTopInventory().getSize() + playerSlot;
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.SHIFT_LEFT, action);
    }

    @Test
    void cancelsEveryInteractionByDefault() {
        SimpleGui gui = Guis.gui().rows(1).build();
        InventoryClickEvent take = menuClick(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(take.isCancelled()).isTrue();
        assertThat(gui.allows(InteractionModifier.ITEM_TAKE)).isFalse();
    }

    @Test
    void cancelAlsoSetsDenyResult() {
        SimpleGui gui = Guis.gui().rows(1).build();
        InventoryClickEvent take = menuClick(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        // The DENY result is the load-bearing half that defeats client-side item prediction.
        assertThat(take.getResult()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void allowedInteractionLeavesResultDefault() {
        SimpleGui gui = Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent take = menuClick(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(take.isCancelled()).isFalse();
        assertThat(take.getResult()).isEqualTo(Event.Result.DEFAULT);
    }

    @Test
    void allowsTakeWhenThatModifierIsEnabled() {
        SimpleGui gui = Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent take = menuClick(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(take.isCancelled()).isFalse(); // taking is allowed, so the event passes through
        assertThat(gui.allows(InteractionModifier.ITEM_TAKE)).isTrue();
    }

    @Test
    void allowingTakeStillCancelsPlace() {
        SimpleGui gui = Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent place = menuClick(gui, 0, InventoryAction.PLACE_ALL);

        gui.handleClick(place);

        assertThat(place.isCancelled()).isTrue(); // only TAKE was allowed; placing is still blocked
    }

    @Test
    void disallowReturnsToCancelling() {
        SimpleGui gui = Guis.gui().rows(1).build();
        gui.allow(InteractionModifier.ITEM_TAKE).disallow(InteractionModifier.ITEM_TAKE);
        InventoryClickEvent take = menuClick(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(take.isCancelled()).isTrue();
    }

    @Test
    void buttonActionStillRunsWhenTakeIsAllowed() {
        SimpleGui gui = Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        boolean[] ran = {false};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> ran[0] = true));
        InventoryClickEvent take = menuClick(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(ran[0]).isTrue(); // the slot action fires regardless of cancellation policy
    }

    @Test
    void hotbarSwapNeedsBothTakeAndSwapAllowed() {
        // A number-key swap pulls the slot's item and swaps in the hotbar item, so it is a take AND a swap.
        SimpleGui takeOnly =
                Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent denied = menuClick(takeOnly, 0, InventoryAction.HOTBAR_SWAP);
        takeOnly.handleClick(denied);
        assertThat(denied.isCancelled()).isTrue(); // SWAP is still disallowed

        SimpleGui both = Guis.gui()
                .rows(1)
                .allow(InteractionModifier.ITEM_TAKE, InteractionModifier.ITEM_SWAP)
                .build();
        InventoryClickEvent allowed = menuClick(both, 0, InventoryAction.HOTBAR_SWAP);
        both.handleClick(allowed);
        assertThat(allowed.isCancelled()).isFalse();
    }

    @Test
    void shiftMoveOutOfMenuIsATake() {
        SimpleGui placeOnly =
                Guis.gui().rows(1).allow(InteractionModifier.ITEM_PLACE).build();
        InventoryClickEvent denied = menuClick(placeOnly, 0, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        placeOnly.handleClick(denied);
        assertThat(denied.isCancelled()).isTrue(); // shift-out of the menu is a take, not a place

        SimpleGui takeOk =
                Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent allowed = menuClick(takeOk, 0, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        takeOk.handleClick(allowed);
        assertThat(allowed.isCancelled()).isFalse();
    }

    @Test
    void shiftMoveFromPlayerInventoryIsAPlace() {
        SimpleGui takeOnly =
                Guis.gui().rows(3).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent denied = playerClick(takeOnly, 0, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        takeOnly.handleClick(denied);
        assertThat(denied.isCancelled()).isTrue(); // shift-in from the player inventory is a place

        SimpleGui placeOk =
                Guis.gui().rows(3).allow(InteractionModifier.ITEM_PLACE).build();
        InventoryClickEvent allowed = playerClick(placeOk, 0, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        placeOk.handleClick(allowed);
        assertThat(allowed.isCancelled()).isFalse();
    }

    @Test
    void collectToCursorIsATake() {
        SimpleGui takeOk =
                Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent allowed = menuClick(takeOk, 0, InventoryAction.COLLECT_TO_CURSOR);
        takeOk.handleClick(allowed);
        assertThat(allowed.isCancelled()).isFalse();

        SimpleGui none = Guis.gui().rows(1).build();
        InventoryClickEvent denied = menuClick(none, 0, InventoryAction.COLLECT_TO_CURSOR);
        none.handleClick(denied);
        assertThat(denied.isCancelled()).isTrue();
    }

    @Test
    void clicksWithinPlayerInventoryAreNeverCancelled() {
        // Picking up from one's own inventory while a menu is open touches nothing the menu owns.
        SimpleGui gui = Guis.gui().rows(3).build();
        InventoryClickEvent own = playerClick(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(own);

        assertThat(own.isCancelled()).isFalse();
        assertThat(own.getResult()).isEqualTo(Event.Result.DEFAULT);
    }
}

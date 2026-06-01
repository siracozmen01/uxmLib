package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

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

    private static InventoryClickEvent clickEvent(Gui gui, int slot, InventoryAction action) {
        var player = MockBukkit.getMock().addPlayer();
        var view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, action);
    }

    @Test
    void cancelsEveryInteractionByDefault() {
        SimpleGui gui = Guis.gui().rows(1).build();
        InventoryClickEvent take = clickEvent(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(take.isCancelled()).isTrue();
        assertThat(gui.allows(InteractionModifier.ITEM_TAKE)).isFalse();
    }

    @Test
    void allowsTakeWhenThatModifierIsEnabled() {
        SimpleGui gui = Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent take = clickEvent(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(take.isCancelled()).isFalse(); // taking is allowed, so the event passes through
        assertThat(gui.allows(InteractionModifier.ITEM_TAKE)).isTrue();
    }

    @Test
    void allowingTakeStillCancelsPlace() {
        SimpleGui gui = Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        InventoryClickEvent place = clickEvent(gui, 0, InventoryAction.PLACE_ALL);

        gui.handleClick(place);

        assertThat(place.isCancelled()).isTrue(); // only TAKE was allowed; placing is still blocked
    }

    @Test
    void disallowReturnsToCancelling() {
        SimpleGui gui = Guis.gui().rows(1).build();
        gui.allow(InteractionModifier.ITEM_TAKE).disallow(InteractionModifier.ITEM_TAKE);
        InventoryClickEvent take = clickEvent(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(take.isCancelled()).isTrue();
    }

    @Test
    void buttonActionStillRunsWhenTakeIsAllowed() {
        SimpleGui gui = Guis.gui().rows(1).allow(InteractionModifier.ITEM_TAKE).build();
        boolean[] ran = {false};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> ran[0] = true));
        InventoryClickEvent take = clickEvent(gui, 0, InventoryAction.PICKUP_ALL);

        gui.handleClick(take);

        assertThat(ran[0]).isTrue(); // the slot action fires regardless of cancellation policy
    }
}

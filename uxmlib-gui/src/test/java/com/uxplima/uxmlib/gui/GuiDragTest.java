package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers the raw-slot drag policy: a drag is cancelled only when it touches the menu's own slots. */
class GuiDragTest {

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

    private static InventoryDragEvent dragOver(Gui gui, int... rawSlots) {
        InventoryView view = openView(gui);
        Map<Integer, ItemStack> newItems = new HashMap<>();
        for (int rawSlot : rawSlots) {
            newItems.put(rawSlot, new ItemStack(Material.STONE));
        }
        return new InventoryDragEvent(view, null, new ItemStack(Material.STONE), false, newItems);
    }

    @Test
    void dragTouchingMenuIsCancelledWhenPlaceDisallowed() {
        SimpleGui gui = Guis.gui().rows(3).build(); // top inventory size 27, no place allowed
        InventoryDragEvent event = dragOver(gui, 0, 1); // raw slots 0,1 are menu slots

        gui.handleDrag(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(event.getResult()).isEqualTo(Event.Result.DENY);
    }

    @Test
    void dragOnlyInPlayerInventoryIsAllowed() {
        SimpleGui gui = Guis.gui().rows(3).build();
        int topSize = openView(gui).getTopInventory().getSize();
        // Use a fresh view of the same menu via a second opening; raw slots past topSize are player slots.
        InventoryDragEvent event = dragOver(gui, topSize, topSize + 1);

        gui.handleDrag(event);

        assertThat(event.isCancelled()).isFalse(); // never touches the menu, so it is left alone
    }

    @Test
    void storageGuiAllowsDragIntoItsSlots() {
        // A StorageGui allows ITEM_PLACE, so even a drag onto its own slots passes through.
        StorageGui gui = Guis.storage().rows(3).build();
        InventoryDragEvent event = dragOver(gui, 0, 1);

        gui.handleDrag(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void dragSpanningBothIsCancelledForButtonMenu() {
        SimpleGui gui = Guis.gui().rows(3).build();
        int topSize = openView(gui).getTopInventory().getSize();
        InventoryDragEvent event = dragOver(gui, 0, topSize); // one menu slot, one player slot

        gui.handleDrag(event);

        assertThat(event.isCancelled()).isTrue(); // any raw slot inside the menu cancels a non-place menu
    }
}

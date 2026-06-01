package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers the row/col placement, addItem auto-fill, getItem, and the GuiFiller helpers. */
class GuiLayoutTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static GuiItem item(Material material) {
        return GuiItem.display(new ItemStack(material));
    }

    @Test
    void rowColMapsToTheRightSlot() {
        SimpleGui gui = Guis.gui().rows(3).build();
        gui.set(2, 1, item(Material.STONE)); // row 2, col 1 -> slot 9
        gui.set(1, 1, item(Material.DIRT)); // row 1, col 1 -> slot 0

        assertThat(gui.getItem(9)).isNotNull();
        assertThat(gui.getItem(0)).isNotNull();
        assertThat(gui.getItem(13)).isNull();
    }

    @Test
    void rowColRejectsBadCoordinates() {
        SimpleGui gui = Guis.gui().rows(3).build();
        assertThatThrownBy(() -> gui.set(0, 1, item(Material.STONE))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gui.set(1, 10, item(Material.STONE))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addItemFillsTheFirstEmptySlots() {
        SimpleGui gui = Guis.gui().rows(1).build();
        gui.set(1, item(Material.BARRIER)); // pre-occupy slot 1
        gui.addItem(item(Material.STONE), item(Material.DIRT));

        assertThat(gui.getItem(0)).isNotNull(); // first empty
        assertThat(gui.getItem(1)).isNotNull(); // the pre-placed barrier, untouched
        assertThat(gui.getItem(2)).isNotNull(); // second added item skipped the occupied slot
    }

    @Test
    void addItemStopsWhenFull() {
        SimpleGui gui = Guis.gui().rows(1).build(); // 9 slots
        GuiItem[] eleven = new GuiItem[11];
        for (int i = 0; i < eleven.length; i++) {
            eleven[i] = item(Material.STONE);
        }
        gui.addItem(eleven); // only 9 fit; the rest drop silently

        for (int slot = 0; slot < 9; slot++) {
            assertThat(gui.getItem(slot)).isNotNull();
        }
    }

    @Test
    void fillBorderLeavesTheCentreEmpty() {
        SimpleGui gui = Guis.gui().rows(3).build();
        gui.filler().fillBorder(item(Material.GRAY_STAINED_GLASS_PANE));

        assertThat(gui.getItem(0)).isNotNull(); // corner
        assertThat(gui.getItem(13)).isNull(); // centre of a 3-row grid
        assertThat(gui.getItem(26)).isNotNull(); // opposite corner
    }

    @Test
    void fillRowAndColumn() {
        SimpleGui gui = Guis.gui().rows(3).build();
        gui.filler().fillRow(1, item(Material.STONE)).fillColumn(1, item(Material.DIRT));

        assertThat(gui.getItem(0)).isNotNull();
        assertThat(gui.getItem(8)).isNotNull(); // end of row 1
        assertThat(gui.getItem(18)).isNotNull(); // column 1, row 3
    }

    @Test
    void fillEmptyDoesNotOverwrite() {
        SimpleGui gui = Guis.gui().rows(1).build();
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        gui.set(4, GuiItem.display(diamond));
        gui.filler().fillEmpty(item(Material.GRAY_STAINED_GLASS_PANE));

        GuiItem atFour = gui.getItem(4);
        assertThat(atFour).isNotNull();
        assertThat(java.util.Objects.requireNonNull(atFour).item()).isEqualTo(diamond); // not overwritten
        assertThat(gui.getItem(0)).isNotNull(); // filled
    }

    @Test
    void defaultClickFiresOnEmptySlots() {
        SimpleGui gui = Guis.gui().rows(1).build();
        boolean[] ran = {false};
        gui.onDefaultClick(e -> ran[0] = true);

        var player = MockBukkit.getMock().addPlayer();
        var view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        var event = new org.bukkit.event.inventory.InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                3, // an empty slot
                org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);

        gui.handleClick(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(ran[0]).isTrue();
    }
}

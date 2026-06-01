package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Drives {@link GuiListener} directly to cover the per-viewer debounce. Without a Scheduler installed the
 * listener runs the slot action inline, so the second rapid click being dropped is observable.
 */
class GuiListenerClickTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static InventoryClickEvent clickFor(PlayerMock player, Gui gui) {
        InventoryView view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        return new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    @Test
    void rapidSecondClickFromSameViewerIsDebounced() {
        GuiListener listener = new GuiListener();
        SimpleGui gui = Guis.gui().rows(1).build();
        int[] runs = {0};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> runs[0]++));
        PlayerMock player = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(player, gui));
        listener.onClick(clickFor(player, gui)); // immediately again, inside the debounce window

        assertThat(runs[0]).isEqualTo(1); // the second click's action is dropped
    }

    @Test
    void debouncedClickIsStillCancelled() {
        GuiListener listener = new GuiListener();
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(player, gui));
        InventoryClickEvent second = clickFor(player, gui);
        listener.onClick(second);

        // Even when the action is debounced, the cancel policy still runs so no item leaks.
        assertThat(second.isCancelled()).isTrue();
    }

    @Test
    void separateViewersAreNotDebouncedAgainstEachOther() {
        GuiListener listener = new GuiListener();
        SimpleGui gui = Guis.gui().rows(1).build();
        int[] runs = {0};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> runs[0]++));
        PlayerMock a = MockBukkit.getMock().addPlayer();
        PlayerMock b = MockBukkit.getMock().addPlayer();

        listener.onClick(clickFor(a, gui));
        listener.onClick(clickFor(b, gui)); // a different viewer, not debounced by a's click

        assertThat(runs[0]).isEqualTo(2);
    }
}

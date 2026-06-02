package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
 * Applies declarative responses in order against a real menu and viewer. The point of side-effects-as-data
 * is that this application step is the only place Bukkit is touched, so it is exercised directly here while
 * handlers stay pure.
 */
class GuiResponsesTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ClickContext clickOn(PlayerMock player, Gui gui) {
        InventoryView view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        return ClickContext.of(event);
    }

    @Test
    void runResponseRunsItsTask() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ClickContext context = clickOn(player, gui);
        boolean[] ran = {false};

        GuiResponses.apply(List.of(GuiResponse.run(() -> ran[0] = true)), gui, context);

        assertThat(ran[0]).isTrue();
    }

    @Test
    void appliesResponsesInOrder() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ClickContext context = clickOn(player, gui);
        List<Integer> order = new java.util.ArrayList<>();

        GuiResponses.apply(
                List.of(GuiResponse.run(() -> order.add(1)), GuiResponse.run(() -> order.add(2))), gui, context);

        assertThat(order).containsExactly(1, 2);
    }

    @Test
    void updateItemPlacesTheItemInTheMenu() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ClickContext context = clickOn(player, gui);
        GuiItem placed = GuiItem.display(new ItemStack(Material.BEACON));

        GuiResponses.apply(List.of(GuiResponse.updateItem(3, placed)), gui, context);

        assertThat(gui.getItem(3)).isSameAs(placed);
    }

    @Test
    void replaceCursorSetsTheViewersCursor() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ClickContext context = clickOn(player, gui);

        GuiResponses.apply(List.of(GuiResponse.replaceCursor(new ItemStack(Material.GOLD_INGOT, 5))), gui, context);

        // The cursor is set on the viewer's live open view, not the (recycled) event object.
        assertThat(player.getOpenInventory().getCursor().getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(player.getOpenInventory().getCursor().getAmount()).isEqualTo(5);
    }

    @Test
    void nothingIsANoOp() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ClickContext context = clickOn(player, gui);

        // Must not throw and must leave the menu untouched.
        GuiResponses.apply(List.of(GuiResponse.nothing()), gui, context);

        assertThat(gui.getItem(0)).isNull();
    }
}

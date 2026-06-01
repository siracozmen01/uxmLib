package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GuiTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void buildsWithTitleAndSize() {
        SimpleGui gui = Guis.gui().title(Component.text("Menu")).rows(3).build();

        assertThat(Component.text("Menu")).isEqualTo(gui.title());
        assertThat(gui.size()).isEqualTo(27);
    }

    @Test
    void rejectsInvalidRowCounts() {
        assertThatThrownBy(() -> Guis.gui().rows(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Guis.gui().rows(7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rendersItemsIntoTheBackingInventory() {
        SimpleGui gui = Guis.gui().rows(1).build();
        ItemStack icon = new ItemStack(Material.DIAMOND);
        gui.set(4, GuiItem.display(icon));

        assertThat(gui.getInventory().getItem(4)).isEqualTo(icon);
    }

    @Test
    void updatesAnOpenInventoryLiveAndClears() {
        SimpleGui gui = Guis.gui().rows(1).build();
        gui.getInventory(); // build it first
        gui.set(0, GuiItem.display(new ItemStack(Material.STONE)));
        assertThat(gui.getInventory().getItem(0)).isNotNull();

        gui.remove(0);
        assertThat(gui.getInventory().getItem(0)).isNull();
    }

    @Test
    void rejectsOutOfRangeSlots() {
        SimpleGui gui = Guis.gui().rows(1).build();
        assertThatThrownBy(() -> gui.set(9, GuiItem.display(new ItemStack(Material.STONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateTitleChangesTheReportedTitle() {
        SimpleGui gui = Guis.gui().title(Component.text("Old")).rows(1).build();
        gui.getInventory(); // build it

        gui.updateTitle(Component.text("New"));

        assertThat(gui.title()).isEqualTo(Component.text("New"));
    }

    @Test
    void updateTitleDoesNotFireOpenOrCloseHandlers() {
        SimpleGui gui = Guis.gui().title(Component.text("Old")).rows(1).build();
        int[] opens = {0};
        int[] closes = {0};
        gui.onOpen(e -> opens[0]++);
        gui.onClose(e -> closes[0]++);

        var player = MockBukkit.getMock().addPlayer();
        gui.open(player);
        int opensAfterRealOpen = opens[0];

        gui.updateTitle(Component.text("New")); // internal reopen must be invisible to handlers

        assertThat(opens[0]).isEqualTo(opensAfterRealOpen);
        assertThat(closes[0]).isZero();
    }

    @Test
    void clickAndOpenSoundsBuildAndOpenWithoutError() {
        net.kyori.adventure.sound.Sound click = net.kyori.adventure.sound.Sound.sound(
                org.bukkit.Sound.UI_BUTTON_CLICK, net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f);
        SimpleGui gui = Guis.gui().rows(1).clickSound(click).openSound(click).build();
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> {}));

        var player = MockBukkit.getMock().addPlayer();
        gui.open(player); // open sound path
        var view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        var event = new org.bukkit.event.inventory.InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,
                org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        gui.handleClick(event); // click sound path

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void applyHookRunsOnTheBuiltMenu() {
        ItemStack icon = new ItemStack(Material.DIAMOND);
        SimpleGui gui =
                Guis.gui().rows(1).apply(g -> g.set(0, GuiItem.display(icon))).build();

        assertThat(gui.getItem(0)).isNotNull();
    }

    @Test
    void clickCancelsByDefaultAndRunsTheSlotAction() {
        SimpleGui gui = Guis.gui().rows(1).build();
        boolean[] ran = {false};
        gui.set(0, GuiItem.button(new ItemStack(Material.STONE), e -> ran[0] = true));

        var player = MockBukkit.getMock().addPlayer();
        var view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        var event = new org.bukkit.event.inventory.InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,
                org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);

        gui.handleClick(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(ran[0]).isTrue();
    }
}

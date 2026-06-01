package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers the navigation back-stack, the back button helper, and programmatic close. */
class GuiNavigatorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void backReopensThePreviousScreen() {
        GuiNavigator nav = new GuiNavigator();
        SimpleGui root = Guis.gui().title(Component.text("Root")).rows(1).build();
        SimpleGui child = Guis.gui().title(Component.text("Child")).rows(1).build();
        Player player = MockBukkit.getMock().addPlayer();

        nav.open(player, root);
        nav.open(player, child);
        assertThat(nav.current(player)).isSameAs(child);
        assertThat(nav.canGoBack(player)).isTrue();

        boolean went = nav.back(player);
        assertThat(went).isTrue();
        assertThat(nav.current(player)).isSameAs(root);
        assertThat(nav.canGoBack(player)).isFalse();
    }

    @Test
    void backOnTheRootDoesNothing() {
        GuiNavigator nav = new GuiNavigator();
        SimpleGui root = Guis.gui().rows(1).build();
        Player player = MockBukkit.getMock().addPlayer();

        nav.open(player, root);
        assertThat(nav.back(player)).isFalse();
        assertThat(nav.current(player)).isSameAs(root);
    }

    @Test
    void openRootResetsTheStack() {
        GuiNavigator nav = new GuiNavigator();
        SimpleGui a = Guis.gui().rows(1).build();
        SimpleGui b = Guis.gui().rows(1).build();
        SimpleGui fresh = Guis.gui().rows(1).build();
        Player player = MockBukkit.getMock().addPlayer();

        nav.open(player, a);
        nav.open(player, b);
        nav.openRoot(player, fresh);

        assertThat(nav.current(player)).isSameAs(fresh);
        assertThat(nav.canGoBack(player)).isFalse();
    }

    @Test
    void backButtonClickPopsTheStack() {
        GuiNavigator nav = new GuiNavigator();
        SimpleGui root = Guis.gui().rows(1).build();
        SimpleGui child = Guis.gui().rows(1).build();
        Player player = MockBukkit.getMock().addPlayer();
        nav.open(player, root);
        nav.open(player, child);

        GuiItem back = GuiItem.back(nav, new ItemStack(Material.ARROW));
        child.set(0, back);
        var view = java.util.Objects.requireNonNull(player.openInventory(child.getInventory()));
        var event = new org.bukkit.event.inventory.InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,
                org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);

        child.handleClick(event);

        assertThat(nav.current(player)).isSameAs(root);
    }

    @Test
    @SuppressWarnings("removal") // the only PlayerQuitEvent constructor available to synthesize the event
    void quitEvictsThePlayerStack() {
        GuiNavigator nav = new GuiNavigator();
        SimpleGui root = Guis.gui().rows(1).build();
        org.bukkit.entity.Player player = MockBukkit.getMock().addPlayer();
        nav.open(player, root);
        assertThat(nav.current(player)).isSameAs(root);

        nav.onQuit(new org.bukkit.event.player.PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty()));

        assertThat(nav.current(player)).isNull(); // stack forgotten on quit
    }

    @Test
    void closeAllClosesEveryViewer() {
        SimpleGui gui = Guis.gui().rows(1).build();
        Player player = MockBukkit.getMock().addPlayer();
        gui.open(player);
        assertThat(player.getOpenInventory().getTopInventory()).isEqualTo(gui.getInventory());

        gui.closeAll();
        // After closing, the player is back to their own inventory view (not the menu).
        assertThat(player.getOpenInventory().getTopInventory()).isNotEqualTo(gui.getInventory());
    }
}

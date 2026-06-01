package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers the per-viewer display-modifier pipeline applied during render. */
class DisplayModifierTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void modifierTransformsTheIconPerViewer() {
        SimpleGui gui = Guis.gui().rows(1).build();
        // A modifier that sets the icon's amount to the viewer's name length.
        DisplayModifier byNameLength = (ctx, base) ->
                new ItemStack(base.getType(), ctx.viewer().getName().length());
        GuiItem item = DisplayModifiers.apply(GuiItem.display(new ItemStack(Material.PAPER)), byNameLength);
        gui.set(0, item);

        Player steve = MockBukkit.getMock().addPlayer("Steve"); // length 5
        gui.open(steve);

        ItemStack rendered = gui.getInventory().getItem(0);
        assertThat(rendered).isNotNull();
        assertThat(rendered.getAmount()).isEqualTo(5);
    }

    @Test
    void pipelineAppliesModifiersInOrder() {
        SimpleGui gui = Guis.gui().rows(1).build();
        DisplayModifier plusOne = (ctx, base) -> new ItemStack(base.getType(), base.getAmount() + 1);
        DisplayModifier timesTwo = (ctx, base) -> new ItemStack(base.getType(), base.getAmount() * 2);
        GuiItem item = DisplayModifiers.apply(
                GuiItem.display(new ItemStack(Material.STONE, 1)), DisplayModifiers.of(plusOne, timesTwo));
        gui.set(0, item);

        Player player = MockBukkit.getMock().addPlayer();
        gui.open(player);

        ItemStack result = gui.getInventory().getItem(0);
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualTo(4); // (1+1)*2
    }

    @Test
    void keepsTheUnderlyingClickAction() {
        SimpleGui gui = Guis.gui().rows(1).build();
        boolean[] ran = {false};
        GuiItem button = GuiItem.button(new ItemStack(Material.STONE), e -> ran[0] = true);
        GuiItem modified = DisplayModifiers.apply(button, (ctx, base) -> base);
        gui.set(0, modified);

        Player player = MockBukkit.getMock().addPlayer();
        var view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        var event = new org.bukkit.event.inventory.InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,
                org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);

        gui.handleClick(event);

        assertThat(ran[0]).isTrue();
    }
}

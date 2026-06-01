package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class GuiTypeTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void hopperMenuHasFiveSlots() {
        assertThat(GuiType.HOPPER.size()).isEqualTo(5);

        SimpleGui gui =
                Guis.typed(GuiType.HOPPER).title(Component.text("Hopper")).build();
        assertThat(gui.size()).isEqualTo(5);
        gui.set(2, GuiItem.display(new ItemStack(Material.DIAMOND)));
        assertThat(gui.getInventory().getItem(2)).isNotNull();
    }

    @Test
    void dispenserMenuHasNineSlots() {
        SimpleGui gui = Guis.typed(GuiType.DISPENSER).build();
        assertThat(gui.size()).isEqualTo(9);
    }

    @Test
    void typedMenuRejectsOutOfRangeSlots() {
        SimpleGui gui = Guis.typed(GuiType.HOPPER).build();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> gui.set(5, GuiItem.display(new ItemStack(Material.STONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void additionalContainerTypesAreUsable() {
        // Each new type maps to a native InventoryType with a sane positive size and builds a menu.
        for (GuiType type : new GuiType[] {
            GuiType.GRINDSTONE, GuiType.STONECUTTER, GuiType.CARTOGRAPHY, GuiType.SMITHING, GuiType.LOOM
        }) {
            assertThat(type.size()).isGreaterThan(0);
            SimpleGui gui = Guis.typed(type).build();
            assertThat(gui.size()).isEqualTo(type.size());
        }
    }
}

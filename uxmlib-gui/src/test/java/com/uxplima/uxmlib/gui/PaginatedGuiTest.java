package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class PaginatedGuiTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static GuiItem item() {
        return GuiItem.display(new ItemStack(Material.STONE));
    }

    @Test
    void computesPageCountFromContentSlots() {
        // Content slots = two slots, so two items per page.
        PaginatedGui gui = Guis.paginated().rows(1).contentSlots(List.of(0, 1)).build();
        for (int i = 0; i < 5; i++) {
            gui.addPageItem(item());
        }

        assertThat(gui.pageCount()).isEqualTo(3); // ceil(5/2)
        assertThat(gui.page()).isZero();
    }

    @Test
    void rendersOnlyTheCurrentPageWindow() {
        PaginatedGui gui = Guis.paginated().rows(1).contentSlots(List.of(0, 1)).build();
        ItemStack a = new ItemStack(Material.DIAMOND);
        ItemStack b = new ItemStack(Material.EMERALD);
        ItemStack c = new ItemStack(Material.GOLD_INGOT);
        gui.addPageItem(GuiItem.display(a));
        gui.addPageItem(GuiItem.display(b));
        gui.addPageItem(GuiItem.display(c));
        gui.render();

        assertThat(gui.getInventory().getItem(0)).isEqualTo(a);
        assertThat(gui.getInventory().getItem(1)).isEqualTo(b);

        gui.next();
        assertThat(gui.page()).isEqualTo(1);
        assertThat(gui.getInventory().getItem(0)).isEqualTo(c);
        assertThat(gui.getInventory().getItem(1)).isNull(); // window not full
    }

    @Test
    void pageNavigationRespectsBounds() {
        PaginatedGui gui = Guis.paginated().rows(1).contentSlots(List.of(0)).build();
        gui.addPageItem(item());
        gui.addPageItem(item());

        gui.previous(); // already on first page
        assertThat(gui.page()).isZero();

        gui.next();
        gui.next(); // only two pages
        assertThat(gui.page()).isEqualTo(1);
    }

    @Test
    void defaultContentSlotsLeaveTheBottomRowFree() {
        PaginatedGui gui = Guis.paginated().rows(3).build();
        // 3 rows: content = first 2 rows = 18 slots.
        for (int i = 0; i < 20; i++) {
            gui.addPageItem(item());
        }
        assertThat(gui.pageCount()).isEqualTo(2); // ceil(20/18)
    }
}

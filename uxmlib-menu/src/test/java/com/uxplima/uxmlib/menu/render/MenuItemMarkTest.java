package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The one persistent-data byte every rendered tile carries, so a display copy that escapes a menu into a real inventory
 * can be told apart from something the player owns. It is defence behind the cancel-all-clicks invariant, not instead
 * of it: the sweep that removes an escaped copy must never remove a genuine item, so a false positive here is the
 * expensive failure and an unmarked tile is the cheap one.
 */
class MenuItemMarkTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMarkedTileIsRecognisedAndAPlayersOwnItemIsNot() {
        assertThat(MenuItemMark.isMarked(MenuItemMark.mark(new ItemStack(Material.STONE))))
                .isTrue();
        assertThat(MenuItemMark.isMarked(new ItemStack(Material.STONE)))
                .as("a genuine item must never look like an escaped tile, or the sweep would eat it")
                .isFalse();
    }

    @Test
    void markingReturnsTheSameStackRatherThanACopy() {
        ItemStack tile = new ItemStack(Material.STONE);

        assertThat(MenuItemMark.mark(tile)).isSameAs(tile);
    }

    @Test
    void theMarkRidesOnTheItemMetaSoItSurvivesTheClonesBukkitMakes() {
        ItemStack tile = MenuItemMark.mark(new ItemStack(Material.STONE));

        assertThat(MenuItemMark.isMarked(tile.clone())).isTrue();
        assertThat(MenuItemMark.isMarked(new ItemStack(tile))).isTrue();
    }

    @Test
    void anEmptySlotIsNotAnEscapedTile() {
        assertThat(MenuItemMark.isMarked(null)).isFalse();
        assertThat(MenuItemMark.isMarked(new ItemStack(Material.AIR))).isFalse();
    }

    @Test
    void theKeyIsTheOneTheSweepLooksFor() {
        assertThat(MenuItemMark.KEY.toString()).isEqualTo("uxmlib:menu");
    }
}

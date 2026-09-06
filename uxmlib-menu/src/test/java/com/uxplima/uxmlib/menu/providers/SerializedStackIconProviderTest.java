package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.item.SerializedItems;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Draws a captured item written into a conf file as one line of text. The interesting part is not the decoding, which
 * belongs to SerializedItems, but that this position answers a damaged token differently from the position that reads
 * it: the library states the damage by throwing, and a menu being drawn has to finish being drawn.
 */
class SerializedStackIconProviderTest {

    private final SerializedStackIconProvider provider = new SerializedStackIconProvider();

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MenuContext ctx() {
        return MenuContext.of(viewer, null, 0);
    }

    @Test
    void aCapturedItemComesBackAsTheTileItWasWrittenFrom() {
        ItemStack captured = new ItemStack(Material.DIAMOND_SWORD, 3);

        assertThat(provider.icon(SerializedItems.encode(captured), ctx()))
                .map(ItemStack::getType)
                .contains(Material.DIAMOND_SWORD);
    }

    @Test
    void aDamagedTokenOpensTheWindowMissingOneTileRatherThanNotOpeningAtAll() {
        String damaged = SerializedItems.PREFIX + "not base64 at all";

        assertThatThrownBy(() -> SerializedItems.decode(damaged))
                .as("from the reader's position a damaged token and a material name must not look alike")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(provider.icon(damaged, ctx()))
                .as("from this position they may, because a menu being drawn has to finish being drawn")
                .isEmpty();
    }

    @Test
    void anythingWithoutThePrefixIsLeftForTheNextProvider() {
        assertThat(provider.icon("DIAMOND_SWORD", ctx())).isEmpty();
        assertThat(provider.icon("skull:Notch", ctx())).isEmpty();
    }
}

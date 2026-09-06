package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Draws one of the viewer's equipped items as a tile. The property that matters is that the tile is a copy: the
 * renderer layers a name and a lore onto whatever this hands back, and the viewer is wearing the original.
 */
class EquipmentIconProviderTest {

    private final EquipmentIconProvider provider = new EquipmentIconProvider();

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
    void eachEquipmentKeywordReadsItsOwnSlot() {
        viewer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        viewer.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        viewer.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        viewer.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        viewer.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        viewer.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));

        assertThat(provider.icon("main_hand", ctx())).map(ItemStack::getType).contains(Material.DIAMOND_SWORD);
        assertThat(provider.icon("off_hand", ctx())).map(ItemStack::getType).contains(Material.SHIELD);
        assertThat(provider.icon("helmet", ctx())).map(ItemStack::getType).contains(Material.IRON_HELMET);
        assertThat(provider.icon("chestplate", ctx())).map(ItemStack::getType).contains(Material.IRON_CHESTPLATE);
        assertThat(provider.icon("leggings", ctx())).map(ItemStack::getType).contains(Material.IRON_LEGGINGS);
        assertThat(provider.icon("boots", ctx())).map(ItemStack::getType).contains(Material.IRON_BOOTS);
    }

    @Test
    void theKeywordIsReadCaseInsensitivelyAndBothSpellingsOfAHandAreAccepted() {
        viewer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        assertThat(provider.icon("  MainHand  ", ctx())).map(ItemStack::getType).contains(Material.DIAMOND_SWORD);
        assertThat(provider.icon("MAIN_HAND", ctx())).map(ItemStack::getType).contains(Material.DIAMOND_SWORD);
    }

    @Test
    void theTileIsACopySoLayeringALoreOntoItCannotReachTheItemTheViewerIsHolding() {
        ItemStack held = new ItemStack(Material.DIAMOND_SWORD);
        viewer.getInventory().setItemInMainHand(held);

        ItemStack tile = provider.icon("main_hand", ctx()).orElseThrow();
        tile.setAmount(64);

        assertThat(viewer.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
    }

    @Test
    void anEmptySlotFallsThroughToTheMaterialFallbackRatherThanRenderingNothing() {
        assertThat(provider.icon("helmet", ctx())).isEmpty();
        assertThat(provider.icon("main_hand", ctx()))
                .as("an empty hand reads as AIR rather than null, and both mean fall through")
                .isEmpty();
    }

    @Test
    void aSpecThatIsNotAnEquipmentKeywordIsNotClaimed() {
        viewer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        assertThat(provider.icon("DIAMOND_SWORD", ctx()))
                .as("a bare material name must reach the material fallback untouched")
                .isEmpty();
        assertThat(provider.icon("skull:Notch", ctx())).isEmpty();
    }
}

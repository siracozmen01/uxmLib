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
 * Draws a list entry that is itself an item as its own tile, which is how a list over raw stacks (a viewer's own
 * inventory, an ender chest) needs no material spec per cell. The tile is a clone, so the list is a read-only view.
 */
class EntryStackIconProviderTest {

    private final EntryStackIconProvider provider = new EntryStackIconProvider();

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

    private MenuContext boundTo(Object entry) {
        return MenuContext.of(viewer, null, 0).withEntry(entry);
    }

    @Test
    void theMarkerDrawsTheBoundStackAsItsOwnTile() {
        assertThat(provider.icon("entry", boundTo(new ItemStack(Material.DIAMOND))))
                .map(ItemStack::getType)
                .contains(Material.DIAMOND);
    }

    @Test
    void theMarkerIsMatchedCaseInsensitivelyAfterTrimming() {
        assertThat(provider.icon("  Entry  ", boundTo(new ItemStack(Material.DIAMOND))))
                .isPresent();
    }

    @Test
    void theTileIsACloneSoWhatTheRendererLayersOnItNeverReachesTheBackingItem() {
        ItemStack backing = new ItemStack(Material.DIAMOND);

        ItemStack tile = provider.icon("entry", boundTo(backing)).orElseThrow();
        tile.setAmount(64);

        assertThat(backing.getAmount())
                .as("a list over a viewer's real inventory must be a read-only view")
                .isEqualTo(1);
    }

    @Test
    void anEntryThatIsNotAnItemFallsThroughRatherThanThrowing() {
        assertThat(provider.icon("entry", boundTo("a warp name")))
                .as("the marker was written on a list of something else, which is a config mistake, not a crash")
                .isEmpty();
    }

    @Test
    void noBoundEntryFallsThroughToTheMaterialFallback() {
        assertThat(provider.icon("entry", MenuContext.of(viewer, null, 0))).isEmpty();
    }

    @Test
    void anythingOtherThanTheMarkerIsLeftForTheNextProvider() {
        MenuContext ctx = boundTo(new ItemStack(Material.DIAMOND));

        assertThat(provider.icon("DIAMOND", ctx)).isEmpty();
        assertThat(provider.icon("entry:0", ctx))
                .as("the marker is an exact word, not a prefix, so it claims no valued token")
                .isEmpty();
    }
}

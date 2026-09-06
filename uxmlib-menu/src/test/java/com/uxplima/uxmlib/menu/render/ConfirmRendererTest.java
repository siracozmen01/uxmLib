package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The engine's own two-button window. It has no spec and no file behind it, so the geometry is in the code and the
 * three things worth pinning are the ones a reader cannot check against anything else: which slot is which decision,
 * that the two are told apart by colour and never swapped, and that neither button carries a word of its own.
 *
 * <p>The last one is the rule about inline literals in test form. A wool button that kept its default name would
 * show a player the English material name, which is the one thing a catalog-driven window must never do.
 */
class ConfirmRendererTest {

    private Inventory inv;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        inv = MockBukkit.getMock().createInventory(null, ConfirmRenderer.ROWS * 9);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theTwoDecisionsAreTheTwoColoursAndNotTheOtherWayRound() {
        new ConfirmRenderer().populate(inv);

        assertThat(inv.getItem(ConfirmRenderer.YES_SLOT)).isNotNull();
        assertThat(inv.getItem(ConfirmRenderer.YES_SLOT).getType()).isEqualTo(Material.LIME_WOOL);
        assertThat(inv.getItem(ConfirmRenderer.NO_SLOT)).isNotNull();
        assertThat(inv.getItem(ConfirmRenderer.NO_SLOT).getType()).isEqualTo(Material.RED_WOOL);
    }

    /** Both slots sit inside the window the geometry claims, so the two constants and the row count agree. */
    @Test
    void bothButtonsFitTheWindowTheRowCountAsksFor() {
        assertThat(ConfirmRenderer.YES_SLOT).isBetween(0, ConfirmRenderer.ROWS * 9 - 1);
        assertThat(ConfirmRenderer.NO_SLOT).isBetween(0, ConfirmRenderer.ROWS * 9 - 1);
        assertThat(ConfirmRenderer.YES_SLOT).isNotEqualTo(ConfirmRenderer.NO_SLOT);
    }

    /**
     * The buttons say nothing. The window's meaning is its title, which the caller resolved from the catalog, so a
     * button that kept the material's own name would put an English word into a window nobody translated.
     */
    @Test
    void neitherButtonCarriesAWordOfItsOwn() {
        new ConfirmRenderer().populate(inv);

        for (int slot : new int[] {ConfirmRenderer.YES_SLOT, ConfirmRenderer.NO_SLOT}) {
            ItemStack button = inv.getItem(slot);
            assertThat(button.getItemMeta().displayName()).isNotNull();
            assertThat(PlainTextComponentSerializer.plainText()
                            .serialize(button.getItemMeta().displayName()))
                    .isEmpty();
            assertThat(button.getItemMeta().hasLore()).isFalse();
        }
    }

    /** Only the two slots are written, so a caller that decorated the window keeps whatever else it put there. */
    @Test
    void nothingButTheTwoSlotsIsTouched() {
        inv.setItem(0, new ItemStack(Material.DIAMOND));

        new ConfirmRenderer().populate(inv);

        assertThat(inv.getItem(0)).isNotNull();
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.DIAMOND);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (slot != 0 && slot != ConfirmRenderer.YES_SLOT && slot != ConfirmRenderer.NO_SLOT) {
                assertThat(inv.getItem(slot)).as("slot " + slot).isNull();
            }
        }
    }
}

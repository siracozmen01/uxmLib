package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.property.SelectorButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The selector child window's paint step. It makes no presentation decision: the property has already built each
 * option's icon, so the only questions here are ordering and coverage. Filler first and buttons second is not a style
 * choice, it is the reason a button is visible at all.
 */
class SelectorRendererTest {

    private final SelectorRenderer renderer = new SelectorRenderer();

    private Inventory inv;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        inv = Bukkit.createInventory(null, 27);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static SelectorButton button(int slot, Material material) {
        return SelectorButton.of(slot, new ItemStack(material), () -> {});
    }

    private Material typeAt(int slot) {
        ItemStack item = inv.getItem(slot);
        return item == null ? Material.AIR : item.getType();
    }

    @Test
    void everySlotIsCoveredSoNoWindowShowsAHole() {
        renderer.populate(inv, Material.GRAY_STAINED_GLASS_PANE, List.of());

        for (int slot = 0; slot < inv.getSize(); slot++) {
            assertThat(typeAt(slot)).as("slot %d", slot).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        }
    }

    @Test
    void anOptionIsDrawnOverTheFillerAndNotUnderIt() {
        renderer.populate(inv, Material.GRAY_STAINED_GLASS_PANE, List.of(button(13, Material.DIAMOND)));

        assertThat(typeAt(13))
                .as("filler painted after the buttons would bury every option")
                .isEqualTo(Material.DIAMOND);
        assertThat(typeAt(12)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(typeAt(14)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void twoButtonsOnOneSlotLeaveTheLastOneWritten() {
        renderer.populate(
                inv,
                Material.GRAY_STAINED_GLASS_PANE,
                List.of(button(4, Material.DIAMOND), button(4, Material.EMERALD)));

        assertThat(typeAt(4)).isEqualTo(Material.EMERALD);
    }

    @Test
    void theFillerCarriesNoNameSoItReadsAsBlankRatherThanAsAMaterial() {
        renderer.populate(inv, Material.GRAY_STAINED_GLASS_PANE, List.of());

        ItemStack filler = Objects.requireNonNull(inv.getItem(0));
        assertThat(filler.hasItemMeta()).isTrue();
        assertThat(Objects.requireNonNull(filler.getItemMeta()).hasDisplayName())
                .as("an unnamed pane would show its material name under the cursor")
                .isTrue();
    }

    @Test
    void repaintingTheSameWindowLeavesNoOptionFromTheDrawBefore() {
        renderer.populate(inv, Material.GRAY_STAINED_GLASS_PANE, List.of(button(13, Material.DIAMOND)));
        renderer.populate(inv, Material.GRAY_STAINED_GLASS_PANE, List.of(button(22, Material.EMERALD)));

        assertThat(typeAt(13))
                .as("a repaint that only wrote the new buttons would leave the old ones clickable")
                .isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(typeAt(22)).isEqualTo(Material.EMERALD);
    }

    @Test
    void aButtonOutsideTheWindowFailsAtTheDrawRatherThanRenderingHalfAMenu() {
        assertThatThrownBy(() ->
                        renderer.populate(inv, Material.GRAY_STAINED_GLASS_PANE, List.of(button(27, Material.DIAMOND))))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}

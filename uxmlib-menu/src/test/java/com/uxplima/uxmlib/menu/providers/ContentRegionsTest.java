package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The slot arithmetic a feature does around its own content region. Everything here copies, because the alternative is
 * a feature's array aliasing the stacks sitting in an open window, which is how an item ends up in two places at once.
 */
class ContentRegionsTest {

    private static final String HOCON = """
            rows = 3
            content { trade { slots = ["10-12"], editable = true } }
            items {}
            """;

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        inventory = MockBukkit.getMock().createInventory(null, 27);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuSpec spec() {
        return new MenuSpecLoader().parse(HOCON);
    }

    @Test
    void aRegionsSlotsComeBackInTheOrderTheFileListsThem() {
        assertThat(ContentRegions.slots(spec(), "trade", "trade.conf")).containsExactly(10, 11, 12);
    }

    @Test
    void aSpecMissingTheRegionFailsAtWiringTimeNamingTheFile() {
        assertThatThrownBy(() -> ContentRegions.slots(spec(), "vault", "trade.conf"))
                .as("a window with nowhere to put items must not open at all")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trade.conf")
                .hasMessageContaining("vault");
    }

    @Test
    void readingARegionYieldsOneEntryPerSlotWithNullWhereTheSlotIsEmpty() {
        inventory.setItem(10, new ItemStack(Material.DIAMOND));
        inventory.setItem(12, new ItemStack(Material.EMERALD));

        ItemStack[] read = ContentRegions.read(inventory, List.of(10, 11, 12));

        assertThat(read).hasSize(3);
        assertThat(read[0]).isNotNull().extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
        assertThat(read[1]).isNull();
        assertThat(read[2]).isNotNull().extracting(ItemStack::getType).isEqualTo(Material.EMERALD);
    }

    @Test
    void whatIsReadIsACopySoAFeaturesArrayNeverAliasesTheOpenWindow() {
        inventory.setItem(10, new ItemStack(Material.DIAMOND));

        ItemStack[] read = ContentRegions.read(inventory, List.of(10));
        read[0].setAmount(64);

        assertThat(inventory.getItem(10))
                .isNotNull()
                .extracting(ItemStack::getAmount)
                .isEqualTo(1);
    }

    @Test
    void clearingARegionEmptiesEveryDeclaredSlotAndNothingElse() {
        inventory.setItem(9, new ItemStack(Material.STONE));
        inventory.setItem(10, new ItemStack(Material.DIAMOND));
        inventory.setItem(12, new ItemStack(Material.EMERALD));

        ContentRegions.clear(inventory, List.of(10, 11, 12));

        assertThat(inventory.getItem(10)).isNull();
        assertThat(inventory.getItem(12)).isNull();
        assertThat(inventory.getItem(9))
                .as("the chrome around a region is an ordinary spec item and must survive")
                .isNotNull();
    }

    @Test
    void copiesPadsToTheRegionSizeSoAShortArrayStillFillsEverySlotPosition() {
        ItemStack[] stacks = {new ItemStack(Material.DIAMOND)};

        assertThat(ContentRegions.copies(stacks, 3)).hasSize(3).containsExactly(stacks[0], null, null);
    }

    @Test
    void copiesTreatsNoStacksAtAllAsAnEmptyRegionRatherThanFailing() {
        assertThat(ContentRegions.copies(null, 2)).hasSize(2).containsOnlyNulls();
    }

    @Test
    void copiesHandsBackCopiesRatherThanTheStacksItWasGiven() {
        ItemStack[] stacks = {new ItemStack(Material.DIAMOND)};

        List<ItemStack> painted = ContentRegions.copies(stacks, 1);
        painted.get(0).setAmount(64);

        assertThat(stacks[0].getAmount()).isEqualTo(1);
    }

    @Test
    void toArrayTruncatesAListLongerThanTheRegionRatherThanOverflowing() {
        List<ItemStack> contents =
                Arrays.asList(new ItemStack(Material.DIAMOND), new ItemStack(Material.EMERALD), null);

        assertThat(ContentRegions.toArray(contents, 2)).hasSize(2);
    }

    @Test
    void anEmptySlotNeverReadsAsAnItem() {
        assertThat(ContentRegions.copyOf(null)).isNull();
        assertThat(ContentRegions.copyOf(new ItemStack(Material.AIR)))
                .as("air and nothing must answer the same, or a feature sees an item it cannot use")
                .isNull();
        assertThat(ContentRegions.copyOf(new ItemStack(Material.DIAMOND))).isNotNull();
    }
}

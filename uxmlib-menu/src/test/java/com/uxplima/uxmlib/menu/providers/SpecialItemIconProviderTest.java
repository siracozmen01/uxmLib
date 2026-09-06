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
 * The two items a material name cannot express, because each is a base material carrying a data value rather than a
 * material of its own. Both are best-effort: a runtime that cannot model the data still yields the base item, because
 * a tile that is slightly wrong is better than a window that will not open.
 */
class SpecialItemIconProviderTest {

    private final SpecialItemIconProvider provider = new SpecialItemIconProvider();

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
    void thereIsNoWaterBottleMaterialSoTheKeywordBuildsThePotionThatIsOne() {
        assertThat(provider.icon("water_bottle", ctx())).map(ItemStack::getType).contains(Material.POTION);
    }

    @Test
    void aLightLevelBuildsALightBlockItem() {
        assertThat(provider.icon("light:7", ctx())).map(ItemStack::getType).contains(Material.LIGHT);
    }

    @Test
    void aLevelOutsideTheRangeOrNotANumberStillYieldsALightRatherThanNothing() {
        assertThat(provider.icon("light:99", ctx())).map(ItemStack::getType).contains(Material.LIGHT);
        assertThat(provider.icon("light:-4", ctx())).map(ItemStack::getType).contains(Material.LIGHT);
        assertThat(provider.icon("light:bright", ctx()))
                .as("a malformed level is a config typo, and it must not stop the window opening")
                .map(ItemStack::getType)
                .contains(Material.LIGHT);
        assertThat(provider.icon("light:", ctx())).map(ItemStack::getType).contains(Material.LIGHT);
    }

    @Test
    void bothKeywordsAreReadCaseInsensitivelyAfterTrimming() {
        assertThat(provider.icon("  Water_Bottle ", ctx())).isPresent();
        assertThat(provider.icon(" LIGHT:3 ", ctx())).isPresent();
    }

    @Test
    void neitherKeywordClaimsABareMaterialName() {
        assertThat(provider.icon("POTION", ctx())).isEmpty();
        assertThat(provider.icon("LIGHT", ctx()))
                .as("light is a prefix with a level, so the bare material must reach the material fallback")
                .isEmpty();
        assertThat(provider.icon("GLASS_BOTTLE", ctx())).isEmpty();
    }

    /**
     * The level is pinned on the parser rather than on the finished stack, because this runtime models a
     * {@code LIGHT} item with a plain {@code ItemMeta} and no {@code BlockDataMeta}, so the provider's
     * {@code editMeta} call is a no-op here and the level never reaches the item. The four assertions above check
     * only that a {@code LIGHT} comes out, which is true whether the level was clamped, set wrongly, or never set
     * at all: they would stay green with the clamp deleted. These do not.
     */
    @Test
    void aLevelInsideTheRangeIsTakenAsWritten() {
        assertThat(SpecialItemIconProvider.clampLevel("7")).isEqualTo(7);
        assertThat(SpecialItemIconProvider.clampLevel(" 7 ")).isEqualTo(7);
    }

    /** Past either end the value is clamped to the end it passed, rather than refused or wrapped. */
    @Test
    void aLevelPastEitherEndIsClampedToThatEnd() {
        assertThat(SpecialItemIconProvider.clampLevel("99")).isEqualTo(15);
        assertThat(SpecialItemIconProvider.clampLevel("-4")).isZero();
    }

    /**
     * A token that is not a number has no end to be clamped to, so it takes the full level. That is a different rule
     * from the clamp above, and the javadoc used to state the two as one.
     */
    @Test
    void aLevelThatIsNotANumberTakesTheFullLevelRatherThanZero() {
        assertThat(SpecialItemIconProvider.clampLevel("bright")).isEqualTo(15);
        assertThat(SpecialItemIconProvider.clampLevel("")).isEqualTo(15);
    }
}

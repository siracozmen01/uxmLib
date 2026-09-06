package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The one part of the MMOItems provider an operator can get wrong. Everything else in that class is reflection that
 * needs the plugin on the server to run at all, so the split is the only place a spelling mistake in a menu file can
 * be answered without one.
 *
 * <p>The trimming is the reason this is worth reading separately. A value that reaches the plugin with a trailing
 * space finds no item, and the failure looks exactly like a wrong id: the plugin says no, the menu shows the
 * material fallback, and nothing anywhere names the space.
 */
class MMOItemsValueTest {

    @Test
    void aValueWithBothHalvesSplitsIntoThem() {
        assertThat(MMOItemsIconProvider.split("SWORD:CUTLASS"))
                .contains(new MMOItemsIconProvider.TypeAndId("SWORD", "CUTLASS"));
    }

    /** Spaces around the inner colon are the operator's own formatting, not part of either half. */
    @Test
    void theSpacesAroundTheInnerColonBelongToNeitherHalf() {
        assertThat(MMOItemsIconProvider.split("SWORD : CUTLASS"))
                .contains(new MMOItemsIconProvider.TypeAndId("SWORD", "CUTLASS"));
        assertThat(MMOItemsIconProvider.split("  SWORD  :  CUTLASS  "))
                .contains(new MMOItemsIconProvider.TypeAndId("SWORD", "CUTLASS"));
    }

    @Test
    void aValueWithNoColonHasNoHalvesToSplit() {
        assertThat(MMOItemsIconProvider.split("SWORD")).isEmpty();
        assertThat(MMOItemsIconProvider.split("")).isEmpty();
    }

    /** A half that is only spaces is as absent as a half that is not there. */
    @Test
    void aHalfThatIsOnlySpacesIsAMissingHalf() {
        assertThat(MMOItemsIconProvider.split(":CUTLASS")).isEmpty();
        assertThat(MMOItemsIconProvider.split("SWORD:")).isEmpty();
        assertThat(MMOItemsIconProvider.split("   :CUTLASS")).isEmpty();
        assertThat(MMOItemsIconProvider.split("SWORD:   ")).isEmpty();
        assertThat(MMOItemsIconProvider.split(" : ")).isEmpty();
    }

    /** Only the first colon splits, so an id that carries one of its own keeps it. */
    @Test
    void onlyTheFirstColonSplitsSoAnIdMayCarryOne() {
        assertThat(MMOItemsIconProvider.split("SWORD:default:cutlass"))
                .contains(new MMOItemsIconProvider.TypeAndId("SWORD", "default:cutlass"));
    }
}

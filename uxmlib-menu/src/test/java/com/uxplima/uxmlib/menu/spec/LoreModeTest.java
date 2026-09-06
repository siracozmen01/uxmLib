package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * How a menu file names the way its lore meets the lore an icon already carries. The mode only matters for an icon
 * that ships lore of its own, so the whole grammar is three words and a default, and the default is the one that
 * matters most: every menu written before the mode existed named nothing, and every one of them has to keep drawing
 * exactly what it drew.
 */
class LoreModeTest {

    @Test
    void theThreeModesAreNamedByTheirOwnWords() {
        assertThat(LoreMode.fromToken("append")).isEqualTo(LoreMode.APPEND);
        assertThat(LoreMode.fromToken("prepend")).isEqualTo(LoreMode.PREPEND);
        assertThat(LoreMode.fromToken("replace")).isEqualTo(LoreMode.REPLACE);
    }

    @Test
    void aTokenIsReadWithoutRegardToCaseOrSurroundingSpace() {
        assertThat(LoreMode.fromToken("  APPEND  ")).isEqualTo(LoreMode.APPEND);
        assertThat(LoreMode.fromToken("\tPrePend\n")).isEqualTo(LoreMode.PREPEND);
    }

    @Test
    void noModeNameCarriesTheLetterThatWouldMakeTheLocaleFoldVisible() {
        // The token is folded with Locale.ROOT, which is right and which no test can currently watch: a plain
        // toLowerCase() answers the same for every name the grammar has, because not one of them holds an I. The
        // property that is real today is the shape of the grammar, so that is what is asserted. This fails the day
        // a mode name gains an I, which is the day the fold needs an assertion of its own.
        for (LoreMode mode : LoreMode.values()) {
            assertThat(mode.name()).doesNotContainIgnoringCase("i");
        }
    }

    @Test
    void aMenuThatNamesNoModeKeepsDrawingWhatItAlwaysDrew() {
        assertThat(LoreMode.fromToken(null)).isEqualTo(LoreMode.REPLACE);
        assertThat(LoreMode.fromToken("")).isEqualTo(LoreMode.REPLACE);
        assertThat(LoreMode.fromToken("   ")).isEqualTo(LoreMode.REPLACE);
    }

    @Test
    void aWordOutsideTheGrammarIsTheDefaultRatherThanARefusal() {
        // Fail-soft on purpose, matching the rest of the spec grammar: a typo in one item's lore-mode must not stop
        // the whole menu loading. The cost is that the typo is silent, which is why the default is the historic
        // behaviour rather than one of the two new ones.
        assertThat(LoreMode.fromToken("apend")).isEqualTo(LoreMode.REPLACE);
        assertThat(LoreMode.fromToken("under")).isEqualTo(LoreMode.REPLACE);
    }

    @Test
    void everyConstantRoundTripsThroughItsOwnName() {
        for (LoreMode mode : LoreMode.values()) {
            assertThat(LoreMode.fromToken(mode.name())).isEqualTo(mode);
        }
    }
}

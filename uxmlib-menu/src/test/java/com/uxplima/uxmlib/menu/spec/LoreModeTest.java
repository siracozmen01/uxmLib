package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

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
    void aTurkishLocaleReadsTheSameTokensAsEveryOtherOne() {
        // The dotless i. Lower-casing "APPEND" in a Turkish default locale is still "append" here only because the
        // token is folded in Locale.ROOT: a plain toLowerCase() would answer "append" for this word and would break
        // on the first mode that carries an I. The property is asserted on the word we have.
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(LoreMode.fromToken("APPEND")).isEqualTo(LoreMode.APPEND);
            assertThat(LoreMode.fromToken("PREPEND")).isEqualTo(LoreMode.PREPEND);
        } finally {
            Locale.setDefault(before);
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

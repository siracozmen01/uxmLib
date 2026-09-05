package com.uxplima.uxmlib.menu.property.colour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * What an operator may type into the picker's anvil. Everything this rejects re-opens the picker with a message, so
 * the cost of accepting something wrong is a colour nobody asked for written into a feature's data, and the cost of
 * rejecting something right is one retype. The line is drawn on the strict side deliberately.
 */
class ColourHexTest {

    @Test
    void sixDigitsAreOpaqueBecauseATypedColourIsAColourAndNotATransparency() {
        assertThat(ColourHex.parse("#A1FF33")).hasValue(0xFFA1FF33);
    }

    @Test
    void eightDigitsKeepTheAlphaAsTyped() {
        assertThat(ColourHex.parse("#80A1FF33")).hasValue(0x80A1FF33);
    }

    @Test
    void theHashIsOptionalBecauseHalfTheWorldTypesItAndHalfDoesNot() {
        assertThat(ColourHex.parse("A1FF33")).isEqualTo(ColourHex.parse("#A1FF33"));
    }

    @Test
    void surroundingSpaceIsNotPartOfTheValue() {
        assertThat(ColourHex.parse("  #A1FF33  ")).hasValue(0xFFA1FF33);
    }

    @Test
    void caseDoesNotMatterInAHexDigit() {
        assertThat(ColourHex.parse("#a1ff33")).isEqualTo(ColourHex.parse("#A1FF33"));
    }

    @Test
    void aWrongLengthIsRejectedRatherThanPaddedOrTruncated() {
        for (String raw : List.of("#FFF", "#A1FF3", "#A1FF333", "#A1FF33333", "#", "")) {
            assertThat(ColourHex.parse(raw))
                    .as("only six and eight digits are a colour: " + raw)
                    .isEmpty();
        }
    }

    @Test
    void aNonHexCharacterIsRejected() {
        assertThat(ColourHex.parse("#GGGGGG")).isEmpty();
        assertThat(ColourHex.parse("#A1 F33")).isEmpty();
    }

    /**
     * A sign is not a hex digit, and it used to get through: Long.parseLong accepts a leading + or -, the six
     * characters fit the length check, and the negative value cast to a plausible-looking ARGB int. So an operator
     * typing a stray dash got a colour rather than the reject message the class promised.
     */
    @Test
    void aSignedNumberIsNotAColourEvenThoughItParsesAsANumber() {
        assertThat(ColourHex.parse("#-12345")).isEmpty();
        assertThat(ColourHex.parse("#+12345")).isEmpty();
        assertThat(ColourHex.parse("-1234567")).isEmpty();
    }

    @Test
    void blankInputIsRejectedRatherThanTreatedAsBlack() {
        assertThat(ColourHex.parse("   ")).isEmpty();
    }

    @Test
    void aFullyOpaqueWhiteAndAFullyTransparentBlackBothSurviveTheRoundTrip() {
        assertThat(ColourHex.parse("#FFFFFFFF")).hasValue(0xFFFFFFFF);
        assertThat(ColourHex.parse("#00000000")).hasValue(0);
    }

    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    @Test
    void aMissingLineIsRefusedRatherThanReadAsNoColour() {
        assertThatNullPointerException().isThrownBy(() -> ColourHex.parse(null)).withMessage("raw");
    }
}

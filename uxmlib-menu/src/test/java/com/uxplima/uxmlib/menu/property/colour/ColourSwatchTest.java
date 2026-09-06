package com.uxplima.uxmlib.menu.property.colour;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The fixed palette. Its order is a contract rather than a detail: an operator's {@code palette-slots} list maps onto
 * it positionally, so reordering the enum silently moves every colour in every already-written layout file.
 */
class ColourSwatchTest {

    @Test
    void thePaletteIsTheSixteenDyeColoursInCanonicalOrder() {
        assertThat(ColourSwatch.palette())
                .as("an operator's palette-slots list is positional, so this order is written into their files")
                .containsExactly(
                        ColourSwatch.WHITE,
                        ColourSwatch.ORANGE,
                        ColourSwatch.MAGENTA,
                        ColourSwatch.LIGHT_BLUE,
                        ColourSwatch.YELLOW,
                        ColourSwatch.LIME,
                        ColourSwatch.PINK,
                        ColourSwatch.GRAY,
                        ColourSwatch.LIGHT_GRAY,
                        ColourSwatch.CYAN,
                        ColourSwatch.PURPLE,
                        ColourSwatch.BLUE,
                        ColourSwatch.BROWN,
                        ColourSwatch.GREEN,
                        ColourSwatch.RED,
                        ColourSwatch.BLACK);
    }

    @Test
    void everySwatchIsOpaqueBecauseAPaletteChoiceIsAColourAndNotAFade() {
        assertThat(ColourSwatch.palette())
                .allSatisfy(swatch -> assertThat(swatch.argb() >>> 24).isEqualTo(0xFF));
    }

    @Test
    void aSwatchesArgbIsItsOwnRgbAndNotTheNextOnes() {
        assertThat(ColourSwatch.WHITE.argb()).isEqualTo(0xFFFFFFFF);
        assertThat(ColourSwatch.BLACK.argb()).isEqualTo(0xFF191919);
        assertThat(ColourSwatch.RED.argb()).isEqualTo(0xFF993333);
    }

    @Test
    void noTwoSwatchesShareAColourOrAnIconOrANameKey() {
        assertThat(ColourSwatch.palette()).extracting(ColourSwatch::argb).doesNotHaveDuplicates();
        assertThat(ColourSwatch.palette()).extracting(ColourSwatch::defaultIcon).doesNotHaveDuplicates();
        assertThat(ColourSwatch.palette()).extracting(ColourSwatch::nameKey).doesNotHaveDuplicates();
    }

    /** A swatch's default icon is what the picker draws when the layout names no material for that slot. */
    @Test
    void everySwatchCarriesADefaultIconSoAnEmptyLayoutStillDrawsAPalette() {
        assertThat(ColourSwatch.palette())
                .allSatisfy(swatch -> assertThat(swatch.defaultIcon()).isNotNull());
    }
}

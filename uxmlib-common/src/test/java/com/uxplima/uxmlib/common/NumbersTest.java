package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class NumbersTest {

    @Test
    void groupsWithThousandsSeparators() {
        assertThat(Numbers.grouped(1_234_567L, Locale.US)).isEqualTo("1,234,567");
        assertThat(Numbers.grouped(-1_000L, Locale.US)).isEqualTo("-1,000");
        assertThat(Numbers.grouped(0L, Locale.US)).isEqualTo("0");
    }

    @Test
    void groupsBelowAThousandUnchanged() {
        assertThat(Numbers.grouped(999L, Locale.US)).isEqualTo("999");
    }

    @Test
    void abbreviatesWithDefaultPrecision() {
        assertThat(Numbers.abbreviate(1_200L)).isEqualTo("1.2K");
        assertThat(Numbers.abbreviate(3_400_000L)).isEqualTo("3.4M");
        assertThat(Numbers.abbreviate(5_600_000_000L)).isEqualTo("5.6B");
        assertThat(Numbers.abbreviate(1_000_000_000_000L)).isEqualTo("1T");
    }

    @Test
    void abbreviatesSmallNumbersWithoutASuffix() {
        assertThat(Numbers.abbreviate(0L)).isEqualTo("0");
        assertThat(Numbers.abbreviate(42L)).isEqualTo("42");
        assertThat(Numbers.abbreviate(999L)).isEqualTo("999");
    }

    @Test
    void abbreviatesNegativesSymmetrically() {
        assertThat(Numbers.abbreviate(-1_500L)).isEqualTo("-1.5K");
    }

    @Test
    void abbreviationDropsATrailingZeroFraction() {
        // 2000 -> "2K", not "2.0K": a whole multiple of the unit shows no decimals.
        assertThat(Numbers.abbreviate(2_000L)).isEqualTo("2K");
        assertThat(Numbers.abbreviate(10_000_000L)).isEqualTo("10M");
    }

    @Test
    void abbreviationRoundsToTheRequestedPrecision() {
        assertThat(Numbers.abbreviate(1_234L, 2)).isEqualTo("1.23K");
        assertThat(Numbers.abbreviate(1_239L, 2)).isEqualTo("1.24K");
        assertThat(Numbers.abbreviate(1_250L, 0)).isEqualTo("1K");
    }

    @Test
    void promotesToTheNextUnitWhenRoundingCrossesABoundary() {
        // 999_500 at precision 0 rounds to 1000K, which must read as 1M, not "1000K".
        assertThat(Numbers.abbreviate(999_500L, 0)).isEqualTo("1M");
        // The same carry at the billion boundary: 999_999_999_999 must read as 1T, not "1000B".
        assertThat(Numbers.abbreviate(999_999_999_999L, 0)).isEqualTo("1T");
        // The carry also applies to negatives.
        assertThat(Numbers.abbreviate(-999_500L, 0)).isEqualTo("-1M");
    }

    @Test
    void abbreviatesTheExtremeNegative() {
        // Math.abs(Long.MIN_VALUE) is still negative, which used to fall through to the raw number.
        assertThat(Numbers.abbreviate(Long.MIN_VALUE, 0)).isEqualTo("-9223372T");
    }

    @Test
    void rejectsNegativePrecision() {
        assertThatThrownBy(() -> Numbers.abbreviate(1_000L, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rendersRomanNumerals() {
        assertThat(Numbers.roman(1)).isEqualTo("I");
        assertThat(Numbers.roman(4)).isEqualTo("IV");
        assertThat(Numbers.roman(9)).isEqualTo("IX");
        assertThat(Numbers.roman(40)).isEqualTo("XL");
        assertThat(Numbers.roman(90)).isEqualTo("XC");
        assertThat(Numbers.roman(400)).isEqualTo("CD");
        assertThat(Numbers.roman(900)).isEqualTo("CM");
        assertThat(Numbers.roman(2024)).isEqualTo("MMXXIV");
        assertThat(Numbers.roman(3999)).isEqualTo("MMMCMXCIX");
    }

    @Test
    void rejectsRomanOutsideTheBoundedRange() {
        assertThatThrownBy(() -> Numbers.roman(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Numbers.roman(-3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Numbers.roman(4000)).isInstanceOf(IllegalArgumentException.class);
    }
}

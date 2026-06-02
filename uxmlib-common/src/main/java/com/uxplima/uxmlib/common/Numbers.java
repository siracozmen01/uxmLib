package com.uxplima.uxmlib.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Stateless number formatting for the places plugins show counts to players: locale-grouped totals
 * ({@code 1,234,567}), compact abbreviations ({@code 1.2K} / {@code 3.4M} / {@code 5.6B}), and Roman
 * numerals ({@code 2024 -> MMXXIV}). Pure static helpers with no mutable state, so they are safe to call
 * from any thread.
 */
public final class Numbers {

    private static final int DEFAULT_PRECISION = 1;

    // Largest-first so the first unit a value reaches is the one used. The empty suffix covers values
    // below a thousand, which are shown as-is.
    private static final long[] UNIT_VALUES = {1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L};
    private static final String[] UNIT_SUFFIXES = {"T", "B", "M", "K"};

    // Roman value/symbol pairs largest-first, including the subtractive forms (IV, IX, XL, ...), so a
    // greedy left-to-right subtraction renders any value in 1..3999.
    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
    };
    private static final int ROMAN_MIN = 1;
    private static final int ROMAN_MAX = 3999;

    private Numbers() {}

    /** Group {@code value} with the thousands separators of {@code locale} (e.g. {@code 1,234,567} for US). */
    public static String grouped(long value, Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return NumberFormat.getIntegerInstance(locale).format(value);
    }

    /** Group {@code value} with {@link Locale#getDefault() the default locale}'s separators. */
    public static String grouped(long value) {
        return grouped(value, Locale.getDefault(Locale.Category.FORMAT));
    }

    /** Abbreviate {@code value} to one decimal place ({@code 1500 -> "1.5K"}, {@code 2000 -> "2K"}). */
    public static String abbreviate(long value) {
        return abbreviate(value, DEFAULT_PRECISION);
    }

    /**
     * Abbreviate {@code value} to at most {@code precision} decimals, dropping a trailing zero fraction so a
     * whole multiple of a unit reads cleanly ({@code 2000 -> "2K"}, not {@code "2.0K"}). Values below a
     * thousand are returned unscaled. Negatives keep their sign.
     *
     * @throws IllegalArgumentException if {@code precision} is negative
     */
    public static String abbreviate(long value, int precision) {
        if (precision < 0) {
            throw new IllegalArgumentException("precision must not be negative: " + precision);
        }
        long magnitude = Math.abs(value);
        for (int i = 0; i < UNIT_VALUES.length; i++) {
            if (magnitude >= UNIT_VALUES[i]) {
                return scaled(value, UNIT_VALUES[i], precision) + UNIT_SUFFIXES[i];
            }
        }
        return Long.toString(value);
    }

    private static String scaled(long value, long unit, int precision) {
        BigDecimal quotient = BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(unit), precision, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return quotient.toPlainString();
    }

    /**
     * Render {@code value} as an upper-case Roman numeral ({@code 4 -> "IV"}, {@code 2024 -> "MMXXIV"}).
     *
     * @throws IllegalArgumentException if {@code value} is outside the representable range {@code 1..3999}
     */
    public static String roman(int value) {
        if (value < ROMAN_MIN || value > ROMAN_MAX) {
            throw new IllegalArgumentException(
                    "roman value must be within [" + ROMAN_MIN + ", " + ROMAN_MAX + "]: " + value);
        }
        StringBuilder out = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (remaining >= ROMAN_VALUES[i]) {
                out.append(ROMAN_SYMBOLS[i]);
                remaining -= ROMAN_VALUES[i];
            }
        }
        return out.toString();
    }
}

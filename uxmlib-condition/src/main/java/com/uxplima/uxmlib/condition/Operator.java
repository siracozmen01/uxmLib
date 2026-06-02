package com.uxplima.uxmlib.condition;

import java.util.List;
import java.util.Objects;

/**
 * The comparison operators a {@link Comparison} understands. The symbols are deliberately the familiar ones
 * from config files. Six order or equate two operands; three more — {@link #CONTAINS}, {@link #WILDCARD} and
 * {@link #OR} — are text operators that match the left operand against the right as a substring, a glob, or a
 * {@code |}-separated alternation. {@link #bySymbolLengthDescending()} fixes the order a longest-symbol-first
 * parser tries them in so a two-character symbol is found before a one-character one that prefixes it (so
 * {@code >=} wins over {@code >}, and the {@code *} of {@link #WILDCARD} is tried last).
 */
public enum Operator {

    /** Equal: numeric equality when both sides are numbers, otherwise case-sensitive string equality. */
    EQUAL("=="),

    /** Not equal: the negation of {@link #EQUAL}. */
    NOT_EQUAL("!="),

    /** Greater-than-or-equal. Numeric only; a non-numeric operand makes it false. */
    GREATER_OR_EQUAL(">="),

    /** Less-than-or-equal. Numeric only; a non-numeric operand makes it false. */
    LESS_OR_EQUAL("<="),

    /** Strictly greater-than. Numeric only; a non-numeric operand makes it false. */
    GREATER(">"),

    /** Strictly less-than. Numeric only; a non-numeric operand makes it false. */
    LESS("<"),

    /** Substring containment: true when the left operand contains the right one. Always a text test. */
    CONTAINS("?="),

    /**
     * Glob match: the right operand is a pattern where {@code *} matches any run of characters and {@code ?}
     * one character; the left operand must match it in full. Always a text test.
     */
    WILDCARD("*"),

    /**
     * Alternation: the right operand is a {@code |}-separated list of branches and the left passes if it
     * equals any one of them under the {@link #EQUAL} rules (numeric per branch when both parse as numbers,
     * trimmed string equality otherwise).
     */
    OR("||");

    private static final List<Operator> BY_SYMBOL_LENGTH =
            List.of(EQUAL, NOT_EQUAL, GREATER_OR_EQUAL, LESS_OR_EQUAL, CONTAINS, OR, GREATER, LESS, WILDCARD);

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    /** The literal operator symbol as it appears in a condition string. */
    public String symbol() {
        return symbol;
    }

    /**
     * Whether this operator orders its operands numerically. Only the four ordering operators ({@code >=},
     * {@code >}, {@code <=}, {@code <}) are numeric-only; the equality operators fall back to string equality
     * and the text operators ({@link #CONTAINS}, {@link #WILDCARD}, {@link #OR}) are always string tests.
     */
    public boolean isOrdering() {
        return switch (this) {
            case GREATER_OR_EQUAL, LESS_OR_EQUAL, GREATER, LESS -> true;
            case EQUAL, NOT_EQUAL, CONTAINS, WILDCARD, OR -> false;
        };
    }

    /**
     * The operators in the order a longest-symbol-first parser should try them: two-character operators
     * before the one-character ones that prefix them.
     */
    static List<Operator> bySymbolLengthDescending() {
        return BY_SYMBOL_LENGTH;
    }

    /** Resolve an operator from its exact symbol, or throw if it is not one we recognise. */
    public static Operator fromSymbol(String symbol) {
        Objects.requireNonNull(symbol, "symbol");
        for (Operator operator : values()) {
            if (operator.symbol.equals(symbol)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("unknown operator symbol: " + symbol);
    }
}

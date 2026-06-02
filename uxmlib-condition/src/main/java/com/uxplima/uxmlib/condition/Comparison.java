package com.uxplima.uxmlib.condition;

import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * A compact, pure comparison between two already-resolved operand strings under one {@link Operator}.
 *
 * <p>Evaluation rules, fixed and documented so config authors can rely on them:
 *
 * <ul>
 *   <li>If <b>both</b> operands parse as <em>finite</em> decimal numbers, the comparison is numeric — so
 *       {@code "1.0" == "1"} and {@code "10" >= "9"} are both true. The numeric test is stricter than {@link
 *       Double#parseDouble}: a Java float-literal suffix ({@code "1d"}, {@code "10F"}), a hex-float form
 *       ({@code "0x1p4"}), and the {@code NaN}/{@code Infinity} tokens are <b>not</b> numbers and fall back to
 *       the string rules below.
 *   <li>Otherwise {@link Operator#EQUAL}/{@link Operator#NOT_EQUAL} fall back to case-sensitive string
 *       equality after trimming surrounding whitespace.
 *   <li>An ordering operator ({@code >=}, {@code >}, {@code <=}, {@code <}) with a non-numeric operand on
 *       either side evaluates to {@code false} — it never throws and never silently equates strings.
 * </ul>
 *
 * <p>This type holds only the operator; operands are passed to {@link #test(String, String)} so one parsed
 * comparison can be reused across many resolved operand pairs. The string-only {@link #parse(String)} reads a
 * fixed {@code left <op> right} form for callers (such as the command {@code @Range}/{@code @Length}
 * validators) that already have both literals in hand.
 */
public final class Comparison {

    // A strict decimal/scientific grammar: optional sign, digits with an optional fractional part (or a
    // leading-dot fraction), and an optional base-10 exponent. This deliberately rejects what Double.valueOf
    // accepts but config authors do not mean as numbers: the d/D/f/F suffixes ("1d", "10F"), hex floats
    // ("0x1p4"), and the NaN/Infinity tokens.
    private static final Pattern STRICT_NUMBER = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private final Operator operator;

    private Comparison(Operator operator) {
        this.operator = operator;
    }

    /** A comparison under the given operator. */
    public static Comparison of(Operator operator) {
        Objects.requireNonNull(operator, "operator");
        return new Comparison(operator);
    }

    /** The operator this comparison applies. */
    public Operator operator() {
        return operator;
    }

    /** Evaluate {@code left <operator> right} under the documented numeric-or-string rules. */
    public boolean test(String left, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Boolean textResult = testTextOperator(left, right);
        if (textResult != null) {
            return textResult;
        }
        Double leftNumber = asNumber(left);
        Double rightNumber = asNumber(right);
        if (leftNumber != null && rightNumber != null) {
            return compareNumbers(leftNumber, rightNumber);
        }
        return compareStrings(left, right);
    }

    // The three text operators never coerce their operands to numbers: a value that happens to look like a
    // number ("1024" containing "10", a group name in a numeric-only realm) is still matched as text. Returns
    // null for the six numeric-or-string operators so the caller falls through to the numeric path.
    private @Nullable Boolean testTextOperator(String left, String right) {
        return switch (operator) {
            case CONTAINS -> left.contains(right);
            case WILDCARD -> globMatches(left.strip(), right.strip());
            case OR -> anyBranchEquals(left, right);
            default -> null;
        };
    }

    private boolean compareNumbers(double left, double right) {
        int sign = Double.compare(left, right);
        return switch (operator) {
            case EQUAL -> sign == 0;
            case NOT_EQUAL -> sign != 0;
            case GREATER_OR_EQUAL -> sign >= 0;
            case LESS_OR_EQUAL -> sign <= 0;
            case GREATER -> sign > 0;
            case LESS -> sign < 0;
            case CONTAINS, WILDCARD, OR -> throw textOperatorOnNumericPath();
        };
    }

    private boolean compareStrings(String left, String right) {
        // Ordering operators are numeric-only: a non-numeric operand can never satisfy them.
        if (operator.isOrdering()) {
            return false;
        }
        boolean equal = left.strip().equals(right.strip());
        return operator == Operator.EQUAL ? equal : !equal;
    }

    // A defensive guard: the text operators are intercepted before either numeric or string comparison runs,
    // so reaching the numeric switch with one means the routing above lost an operator.
    private IllegalStateException textOperatorOnNumericPath() {
        return new IllegalStateException("text operator " + operator + " must not reach the numeric path");
    }

    // Anchored glob: '*' matches any run (including empty), '?' exactly one character; every other character is
    // literal. The whole left operand must be consumed. Iterative with a single backtrack point so a pattern
    // can never blow the stack on adversarial input.
    private static boolean globMatches(String text, String pattern) {
        int t = 0;
        int p = 0;
        int star = -1;
        int matchedAtStar = 0;
        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == text.charAt(t))) {
                t++;
                p++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                matchedAtStar = t;
            } else if (star >= 0) {
                p = star + 1;
                t = ++matchedAtStar;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }

    // Split the right operand on '|' into branches and pass if any branch equals the left under EQUAL's rules.
    private static boolean anyBranchEquals(String left, String branches) {
        Comparison equality = of(Operator.EQUAL);
        for (String branch : branches.split("\\|", -1)) {
            if (equality.test(left, branch)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Double asNumber(String value) {
        String trimmed = value.strip();
        if (trimmed.isEmpty() || !STRICT_NUMBER.matcher(trimmed).matches()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * Parse a {@code left <op> right} expression where the operator is one of {@link Operator}'s symbols.
     * The first operator symbol found by a longest-match scan splits the two operands. A {@code %...%}
     * placeholder span — a pair of {@code %} markers with no whitespace between them, the PlaceholderAPI
     * shape — is skipped whole, so an operator character inside a placeholder body (such as the {@code <} in
     * {@code %math_2<3%}) never splits the expression. A bare {@code %} that is not part of such a span (a
     * literal percent sign like {@code 50%}) is treated as an ordinary character. Throws {@link
     * IllegalArgumentException} if no known operator appears.
     */
    public static ParsedComparison parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        for (int i = 0; i < expression.length(); i++) {
            int placeholderEnd = placeholderEnd(expression, i);
            if (placeholderEnd > i) {
                i = placeholderEnd; // jump to the closing '%'; the loop step moves past it
                continue;
            }
            Operator operator = operatorAt(expression, i);
            if (operator != null) {
                String left = expression.substring(0, i).strip();
                String right =
                        expression.substring(i + operator.symbol().length()).strip();
                return new ParsedComparison(of(operator), left, right);
            }
        }
        throw new IllegalArgumentException("no comparison operator in: " + expression);
    }

    /**
     * If {@code index} opens a PlaceholderAPI-shaped {@code %...%} span (a closing {@code %} with no
     * whitespace in between), return the index of that closing {@code %}; otherwise return {@code index} so the
     * character is treated literally. This keeps an operator symbol inside a placeholder body out of the split
     * while leaving a literal percent sign (no balanced, whitespace-free partner) to be scanned normally.
     */
    private static int placeholderEnd(String expression, int index) {
        if (expression.charAt(index) != '%') {
            return index;
        }
        for (int j = index + 1; j < expression.length(); j++) {
            char c = expression.charAt(j);
            if (c == '%') {
                return j;
            }
            if (Character.isWhitespace(c)) {
                return index;
            }
        }
        return index;
    }

    private static @Nullable Operator operatorAt(String expression, int index) {
        for (Operator operator : Operator.bySymbolLengthDescending()) {
            if (expression.startsWith(operator.symbol(), index)) {
                return operator;
            }
        }
        return null;
    }

    /** A parsed {@code left <op> right} expression: the comparison plus its two literal operands. */
    public record ParsedComparison(Comparison comparison, String left, String right) {

        /** Canonical constructor null-checks every component. */
        public ParsedComparison {
            Objects.requireNonNull(comparison, "comparison");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }

        /** Evaluate the parsed literals directly. */
        public boolean evaluate() {
            return comparison.test(left, right);
        }
    }
}

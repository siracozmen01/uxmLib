package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Pure evaluator coverage: every operator, numeric vs string fallback, and malformed input behaviour. */
class ComparisonTest {

    @ParameterizedTest
    @CsvSource({
        // numeric equality, including different textual forms of the same number
        "EQUAL, 1, 1, true",
        "EQUAL, 1.0, 1, true",
        "EQUAL, 1, 2, false",
        "NOT_EQUAL, 1, 2, true",
        "NOT_EQUAL, 1.0, 1, false",
        // ordering, numeric
        "GREATER, 10, 9, true",
        "GREATER, 9, 10, false",
        "GREATER, 5, 5, false",
        "GREATER_OR_EQUAL, 5, 5, true",
        "GREATER_OR_EQUAL, 4, 5, false",
        "LESS, 9, 10, true",
        "LESS, 10, 9, false",
        "LESS_OR_EQUAL, 5, 5, true",
        "LESS_OR_EQUAL, 6, 5, false",
    })
    void numericComparisons(Operator operator, String left, String right, boolean expected) {
        assertThat(Comparison.of(operator).test(left, right)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        // both sides non-numeric -> string equality for ==/!=
        "EQUAL, world, world, true",
        "EQUAL, world, nether, false",
        "NOT_EQUAL, world, nether, true",
        "NOT_EQUAL, world, world, false",
    })
    void stringEqualityWhenNotNumeric(Operator operator, String left, String right, boolean expected) {
        assertThat(Comparison.of(operator).test(left, right)).isEqualTo(expected);
    }

    @Test
    void stringEqualityIsCaseSensitive() {
        assertThat(Comparison.of(Operator.EQUAL).test("World", "world")).isFalse();
    }

    @Test
    void surroundingWhitespaceIsTrimmedForStringEquality() {
        assertThat(Comparison.of(Operator.EQUAL).test("  world ", "world")).isTrue();
    }

    @Test
    void mixedNumericAndStringFallsBackToStringEquality() {
        // one side numeric, the other not: not both numbers, so == compares as strings ("10" != "ten")
        assertThat(Comparison.of(Operator.EQUAL).test("10", "ten")).isFalse();
        assertThat(Comparison.of(Operator.NOT_EQUAL).test("10", "ten")).isTrue();
    }

    @Test
    void orderingOperatorWithNonNumericOperandIsFalseNeverThrows() {
        assertThat(Comparison.of(Operator.GREATER).test("abc", "10")).isFalse();
        assertThat(Comparison.of(Operator.LESS_OR_EQUAL).test("10", "abc")).isFalse();
        assertThat(Comparison.of(Operator.GREATER_OR_EQUAL).test("foo", "bar")).isFalse();
    }

    @Test
    void emptyOperandIsTreatedAsNonNumeric() {
        // an empty resolved placeholder must not parse as a number and must not throw
        assertThat(Comparison.of(Operator.GREATER).test("", "5")).isFalse();
        assertThat(Comparison.of(Operator.EQUAL).test("", "")).isTrue();
    }

    @Test
    void parseSplitsOnLongestOperatorFirst() {
        Comparison.ParsedComparison parsed = Comparison.parse("%player_health% >= 10");
        assertThat(parsed.comparison().operator()).isEqualTo(Operator.GREATER_OR_EQUAL);
        assertThat(parsed.left()).isEqualTo("%player_health%");
        assertThat(parsed.right()).isEqualTo("10");
    }

    @Test
    void parseEvaluatesLiteralOperands() {
        assertThat(Comparison.parse("10 >= 5").evaluate()).isTrue();
        assertThat(Comparison.parse("foo == foo").evaluate()).isTrue();
        assertThat(Comparison.parse("3 < 2").evaluate()).isFalse();
    }

    @Test
    void parseRejectsExpressionWithoutOperator() {
        assertThatThrownBy(() -> Comparison.parse("just text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no comparison operator");
    }

    @Test
    void parseIgnoresOperatorCharactersInsideAPlaceholderBody() {
        // The '<' lives inside %math_2<3%; the real operator is the '==' that follows the placeholder.
        Comparison.ParsedComparison parsed = Comparison.parse("%math_2<3% == true");
        assertThat(parsed.comparison().operator()).isEqualTo(Operator.EQUAL);
        assertThat(parsed.left()).isEqualTo("%math_2<3%");
        assertThat(parsed.right()).isEqualTo("true");
    }

    @Test
    void parseIgnoresEqualsAndGreaterInsideAPlaceholderBody() {
        Comparison.ParsedComparison parsed = Comparison.parse("%a>=b% != %c==d%");
        assertThat(parsed.comparison().operator()).isEqualTo(Operator.NOT_EQUAL);
        assertThat(parsed.left()).isEqualTo("%a>=b%");
        assertThat(parsed.right()).isEqualTo("%c==d%");
    }

    @Test
    void parseStillSplitsOnAnOperatorAfterAnUnterminatedPlaceholder() {
        // A lone '%' with no closing '%' must not swallow the rest of the line, or no operator is ever found.
        Comparison.ParsedComparison parsed = Comparison.parse("50% == 50%");
        assertThat(parsed.comparison().operator()).isEqualTo(Operator.EQUAL);
        assertThat(parsed.left()).isEqualTo("50%");
        assertThat(parsed.right()).isEqualTo("50%");
    }

    @ParameterizedTest
    @CsvSource({
        // CONTAINS: left contains right as a substring (case-sensitive), never numeric.
        "CONTAINS, hello world, world, true",
        "CONTAINS, hello world, mars, false",
        "CONTAINS, world, world, true",
        "CONTAINS, World, world, false",
        // a numeric-looking pair still uses substring containment, not numeric equality
        "CONTAINS, 1234, 23, true",
        "CONTAINS, 12, 123, false",
    })
    void containsTestsSubstringInclusion(Operator operator, String left, String right, boolean expected) {
        assertThat(Comparison.of(operator).test(left, right)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        // WILDCARD: the right operand is a glob (* = any run, ? = one char); the left must match it whole.
        "WILDCARD, world_nether, world_*, true",
        "WILDCARD, world_nether, *_nether, true",
        "WILDCARD, world_nether, w*r, true",
        "WILDCARD, world_nether, nether, false",
        "WILDCARD, cat, c?t, true",
        "WILDCARD, coat, c?t, false",
        "WILDCARD, anything, *, true",
        // glob is anchored: a pattern that does not cover the whole left fails
        "WILDCARD, world_nether, world, false",
        // a numeric-looking pair still globs as text
        "WILDCARD, 1024, 10*, true",
    })
    void wildcardTestsGlobMatch(Operator operator, String left, String right, boolean expected) {
        assertThat(Comparison.of(operator).test(left, right)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        // OR: the right operand is a '|'-separated alternation; left passes if it equals any branch.
        "OR, world, world|nether|the_end, true",
        "OR, nether, world|nether|the_end, true",
        "OR, the_end, world|nether|the_end, true",
        "OR, aether, world|nether|the_end, false",
        // a single branch is just equality
        "OR, world, world, true",
        "OR, world, nether, false",
        // numeric branches compare numerically per branch, so 1.0 matches a 1 branch
        "OR, 1.0, 1|2|3, true",
        "OR, 4, 1|2|3, false",
    })
    void orTestsAlternationOfBranches(Operator operator, String left, String right, boolean expected) {
        assertThat(Comparison.of(operator).test(left, right)).isEqualTo(expected);
    }

    @Test
    void orBranchesAreTrimmedBeforeComparison() {
        assertThat(Comparison.of(Operator.OR).test("nether", "world | nether | the_end"))
                .isTrue();
    }

    @Test
    void containsAndWildcardEvaluateThroughParse() {
        assertThat(Comparison.parse("%player_world% ?= nether").comparison().operator())
                .isEqualTo(Operator.CONTAINS);
        assertThat(Comparison.parse("%player_group% || admin|owner")
                        .comparison()
                        .operator())
                .isEqualTo(Operator.OR);
    }

    @ParameterizedTest
    @CsvSource({
        // Java float-literal suffixes must be treated as plain strings, not numbers.
        "EQUAL, 1d, 1, false",
        "EQUAL, 10F, 10, false",
        "EQUAL, 5f, 5, false",
        // Hex float forms must not parse as numbers (0x1p4 == 16.0 under Double.valueOf).
        "EQUAL, 0x1p4, 16, false",
        // NaN/Infinity must compare as strings, not as numbers.
        "EQUAL, NaN, NaN, true",
        "GREATER, Infinity, 5, false",
        "LESS, -Infinity, 5, false",
    })
    void leadingLenientNumericFormsAreTreatedAsStrings(Operator operator, String left, String right, boolean expected) {
        assertThat(Comparison.of(operator).test(left, right)).isEqualTo(expected);
    }
}

package com.uxplima.uxmlib.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The value semantics under the expression language: what coerces, what compares, and what is refused. The parser
 * tests reach these through written expressions, which proves the common paths and hides the edges, because an
 * operator does not write {@code 1 / 0} in the expression a test author chooses to write.
 *
 * <p>Two of these decisions are visible to an operator and neither is obvious. A comparison between a number and a
 * piece of text is decided as text, so {@code 50 == "50"} holds. And a number formats without its trailing zero, so
 * an evaluated {@code 50.0} reaches a menu line as {@code 50} rather than as something no operator wrote.
 */
class ValuesTest {

    // -- coercion ------------------------------------------------------------------------------------------

    @Test
    void aNumberCoercesToItself() throws ExpressionException {
        assertThat(Values.number(2.5d)).isEqualTo(2.5d);
    }

    /** The message names what was found, because an operator's next question is which side of the expression is wrong. */
    @Test
    void askingForANumberAndFindingTextSaysWhichItFound() {
        assertThatThrownBy(() -> Values.number("hello"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected a number")
                .hasMessageContaining("text");
    }

    @Test
    void askingForANumberAndFindingABooleanSaysSo() {
        assertThatThrownBy(() -> Values.number(true))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("a boolean");
    }

    @Test
    void aBooleanCoercesToItself() throws ExpressionException {
        assertThat(Values.bool(true)).isTrue();
        assertThat(Values.bool(false)).isFalse();
    }

    @Test
    void askingForABooleanAndFindingANumberSaysSo() {
        assertThatThrownBy(() -> Values.bool(1.0d))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected a boolean")
                .hasMessageContaining("a number");
    }

    // -- finiteness ----------------------------------------------------------------------------------------

    @Test
    void aFiniteNumberPassesThroughUnchanged() throws ExpressionException {
        assertThat(Values.finite(-3.5d)).isEqualTo(-3.5d);
    }

    @Test
    void neitherInfinityNorNaNIsANumberAnExpressionMayYield() {
        assertThatThrownBy(() -> Values.finite(Double.POSITIVE_INFINITY)).isInstanceOf(ExpressionException.class);
        assertThatThrownBy(() -> Values.finite(Double.NEGATIVE_INFINITY)).isInstanceOf(ExpressionException.class);
        assertThatThrownBy(() -> Values.finite(Double.NaN)).isInstanceOf(ExpressionException.class);
    }

    /** An overflow is caught where it happens rather than travelling into a menu line as the word "Infinity". */
    @Test
    void anArithmeticOverflowIsRefusedRatherThanRendered() {
        assertThatThrownBy(() -> Values.arithmetic(Double.MAX_VALUE, "*", 10d))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("finite");
    }

    // -- formatting ----------------------------------------------------------------------------------------

    @Test
    void anIntegralValueLosesItsTrailingZeroBecauseNoOperatorWroteOne() {
        assertThat(Values.format(50d)).isEqualTo("50");
        assertThat(Values.format(0d)).isEqualTo("0");
        assertThat(Values.format(-7d)).isEqualTo("-7");
    }

    @Test
    void aFractionalValueKeepsItsDecimals() {
        assertThat(Values.format(2.5d)).isEqualTo("2.5");
    }

    /**
     * The guard is at the point where a double stops holding integers exactly. Below it the integral path is safe;
     * at and above it the decimal form is the honest one, because the long conversion would state a precision the
     * value does not have.
     */
    @Test
    void aValueTooLargeToHoldExactlyStaysOnTheDecimalPath() {
        assertThat(Values.format(1e14)).isEqualTo("100000000000000");
        assertThat(Values.format(1e15)).contains("E");
    }

    @Test
    void aValueThatIsNotFiniteFormatsRatherThanFailing() {
        assertThat(Values.format(Double.NaN)).isEqualTo("NaN");
    }

    // -- arithmetic ----------------------------------------------------------------------------------------

    @Test
    void theFiveOrdinaryOperatorsDoWhatTheySay() throws ExpressionException {
        assertThat(Values.arithmetic(7d, "+", 3d)).isEqualTo(10d);
        assertThat(Values.arithmetic(7d, "-", 3d)).isEqualTo(4d);
        assertThat(Values.arithmetic(7d, "*", 3d)).isEqualTo(21d);
        assertThat(Values.arithmetic(8d, "/", 2d)).isEqualTo(4d);
        assertThat(Values.arithmetic(7d, "%", 3d)).isEqualTo(1d);
        assertThat(Values.arithmetic(2d, "^", 10d)).isEqualTo(1024d);
    }

    /**
     * Division by zero is refused by name rather than left to produce an infinity. The finite guard would catch it
     * anyway, so the reason for a separate check is the message: "division by zero" tells an operator where to look
     * and "result is not a finite number" does not.
     */
    @Test
    void dividingByZeroSaysThatIsWhatHappened() {
        assertThatThrownBy(() -> Values.arithmetic(1d, "/", 0d))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("division by zero");
    }

    @Test
    void moduloByZeroSaysThatIsWhatHappened() {
        assertThatThrownBy(() -> Values.arithmetic(1d, "%", 0d))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("modulo by zero");
    }

    @Test
    void anOperatorTheEvaluatorDoesNotKnowIsNamedInTheFailure() {
        assertThatThrownBy(() -> Values.arithmetic(1d, "**", 2d))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("**");
    }

    // -- comparison ----------------------------------------------------------------------------------------

    @Test
    void twoNumbersCompareAsNumbers() throws ExpressionException {
        assertThat(Values.compare(2d, "<", 10d)).isTrue();
        assertThat(Values.compare(2d, ">", 10d)).isFalse();
        assertThat(Values.compare(2d, "<=", 2d)).isTrue();
        assertThat(Values.compare(2d, ">=", 2d)).isTrue();
        assertThat(Values.compare(2d, "==", 2d)).isTrue();
        assertThat(Values.compare(2d, "!=", 2d)).isFalse();
    }

    /**
     * Ten is less than nine as text and greater than nine as a number. The two operands being numbers is what picks
     * the numeric path, and this is the assertion that says the evaluator is not comparing digits.
     */
    @Test
    void aNumericComparisonIsNotAnAlphabeticalOne() throws ExpressionException {
        assertThat(Values.compare(10d, ">", 9d)).isTrue();
    }

    /**
     * A number against a piece of text is decided as text, using the same rendering a number takes into a menu line.
     * So {@code 50 == "50"} holds, which is the behaviour a placeholder comparison depends on: a placeholder that
     * resolved to text still matches the number an operator wrote beside it.
     */
    @Test
    void aNumberAndTextCompareAsTextUsingTheSameRenderingAMenuLineGets() throws ExpressionException {
        assertThat(Values.compare(50d, "==", "50")).isTrue();
        assertThat(Values.compare(50d, "!=", "50")).isFalse();
        assertThat(Values.compare(50d, "==", "50.0")).isFalse();
    }

    @Test
    void twoBooleansCompareAsTheirOwnWords() throws ExpressionException {
        assertThat(Values.compare(true, "==", true)).isTrue();
        assertThat(Values.compare(true, "!=", false)).isTrue();
    }

    /**
     * Ordering is only defined for numbers. Ordering text would have to pick a collation, and a menu that sorted
     * differently on two servers would be worse than one that says it cannot.
     */
    @Test
    void orderingTwoPiecesOfTextIsRefusedRatherThanGuessedAt() {
        assertThatThrownBy(() -> Values.compare("a", "<", "b"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("numeric operands");
    }

    @Test
    void aComparisonTheEvaluatorDoesNotKnowIsNamedInTheFailure() {
        assertThatThrownBy(() -> Values.compare(1d, "<>", 2d))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("<>");
    }
}

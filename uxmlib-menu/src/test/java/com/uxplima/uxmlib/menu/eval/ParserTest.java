package com.uxplima.uxmlib.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The grammar, read through its edges rather than its centre. {@link ExpressionsTest} already shows that the
 * ordinary shapes evaluate; what is pinned here is where the parser stops: the trailing token it refuses to
 * ignore, the chain it will not build, the two spellings of each boolean operator, and the messages an operator
 * reads when the expression in a menu config is wrong.
 *
 * <p>The parser evaluates as it walks, so it has no syntax tree to inspect. Everything below is therefore stated
 * as a result or as a refusal, which is also the only surface a menu config can observe.
 */
class ParserTest {

    // -- the walk ends where the expression does -------------------------------------------------------------

    /** A leftover token is an error rather than a silent stop, so a typo cannot half-evaluate. */
    @Test
    void aTokenLeftOverAfterACompleteExpressionIsRefused() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("1 + 1 2"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected END");
    }

    /**
     * A number token carries its value rather than its spelling, so a message that reaches for the token text has
     * nothing to show. The one that names the leftover token formats the number the way the evaluator formats any
     * other, which is the difference between "found 2" and "found ".
     */
    @Test
    void aLeftOverNumberIsNamedInTheMessageRatherThanLeftBlank() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("1 + 1 2"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageEndingWith("2");
        assertThatThrownBy(() -> Expressions.evaluateNumber("1 + 1 2.5"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageEndingWith("2.5");
    }

    /** An expression that stops in the middle names the end of the input rather than an empty token text. */
    @Test
    void anExpressionThatStopsInTheMiddleNamesTheEndOfTheInput() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("1 +"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("end of input");
        assertThatThrownBy(() -> Expressions.evaluateNumber("(1"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected RPAREN")
                .hasMessageContaining("end of input");
    }

    @Test
    void anEmptyExpressionIsRefusedRatherThanTreatedAsZero() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("   ")).isInstanceOf(ExpressionException.class);
    }

    // -- what the grammar will not build ----------------------------------------------------------------------

    /**
     * A comparison takes one operator and no more, so {@code 1 < 2 < 3} does not quietly become a comparison of
     * the first comparison's answer against 3. The second comparator is left over and the parse refuses it.
     */
    @Test
    void comparisonsDoNotChain() {
        assertThatThrownBy(() -> Expressions.evaluateBoolean("1 < 2 < 3"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected END");
    }

    // -- two spellings of the same operator --------------------------------------------------------------------

    @Test
    void theWordAndTheSymbolAreTheSameBooleanOperator() throws ExpressionException {
        assertThat(Expressions.evaluateBoolean("true and false"))
                .isEqualTo(Expressions.evaluateBoolean("true && false"));
        assertThat(Expressions.evaluateBoolean("true or false"))
                .isEqualTo(Expressions.evaluateBoolean("true || false"));
        assertThat(Expressions.evaluateBoolean("not true")).isEqualTo(Expressions.evaluateBoolean("!true"));
    }

    @Test
    void negationStacks() throws ExpressionException {
        assertThat(Expressions.evaluateBoolean("!!true")).isTrue();
        assertThat(Expressions.evaluateBoolean("not not false")).isFalse();
    }

    @Test
    void theBooleanLiteralsAreSpelledInLowerCaseOnly() {
        assertThatThrownBy(() -> Expressions.evaluateBoolean("TRUE"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unknown identifier: TRUE");
    }

    /**
     * Neither boolean operator short circuits. The parser must consume the right operand's tokens whether or not
     * the left already settles the answer, and it evaluates as it consumes, so a failing right operand fails the
     * whole expression. An operator writing a guard in a menu config cannot lean on the left side to protect the
     * right one.
     */
    @Test
    void aFailingRightOperandFailsTheExpressionEvenWhenTheLeftAlreadySettlesIt() {
        assertThatThrownBy(() -> Expressions.evaluateBoolean("true || 1 / 0 > 0"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("zero");
        assertThatThrownBy(() -> Expressions.evaluateBoolean("false && 1 / 0 > 0"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("zero");
    }

    // -- calls ---------------------------------------------------------------------------------------------------

    @Test
    void aFunctionNameWithoutItsBracketsSaysSoRatherThanCallingTheNameUnknown() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("min + 1"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("min is a function")
                .hasMessageNotContaining("unknown identifier");
    }

    @Test
    void aNameNoFunctionHasIsStillAnUnknownIdentifier() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("balance(1)"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unknown identifier: balance");
    }

    @Test
    void aCallWithNoArgumentsReachesTheFunctionAndIsRefusedByIt() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("min()"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("min needs at least one argument");
    }

    @Test
    void aTrailingCommaInACallIsRefused() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("min(1,)"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unexpected token");
    }

    /** Each argument is a whole expression, so a call nests and an argument may carry its own arithmetic. */
    @Test
    void anArgumentIsAWholeExpressionAndNotJustANumber() throws ExpressionException {
        assertThat(Expressions.evaluateNumber("min(2 + 3, max(1, 4))")).isEqualTo(4d);
    }

    /** An argument that evaluates to a boolean is refused: every function takes numbers. */
    @Test
    void anArgumentThatIsNotANumberIsRefusedByName() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("abs(1 == 1)"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected a number");
    }

    // -- unary and power -------------------------------------------------------------------------------------------

    @Test
    void aLeadingPlusIsReadAndChangesNothing() throws ExpressionException {
        assertThat(Expressions.evaluateNumber("+7")).isEqualTo(7d);
        assertThat(Expressions.evaluateNumber("2 * +3")).isEqualTo(6d);
    }

    @Test
    void aMinusStacksAndTheExponentMayCarryOne() throws ExpressionException {
        assertThat(Expressions.evaluateNumber("--5")).isEqualTo(5d);
        assertThat(Expressions.evaluateNumber("2 ^ -1")).isEqualTo(0.5d);
    }

    /**
     * A unary sign takes a number, so it cannot be put in front of a boolean or a string. Both signs are checked:
     * a leading plus changes nothing about the value, which makes it easy to write as a pass-through, and a
     * pass-through would let {@code +true} evaluate to true instead of failing.
     */
    @Test
    void aSignInFrontOfSomethingThatIsNotANumberIsRefused() {
        assertThatThrownBy(() -> Expressions.evaluateNumber("-true"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected a number");
        assertThatThrownBy(() -> Expressions.evaluateBoolean("+true"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("expected a number");
    }
}

package com.uxplima.uxmlib.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The complete function surface an expression may reach. The class is an allow-list, so the test that matters most is
 * not that each function computes correctly but that the list is exactly this and that a name outside it is refused:
 * the whole safety argument for evaluating an operator's arithmetic is that nothing here reflects, performs I/O, or
 * reaches the host runtime, and that argument is only as good as the list being closed.
 *
 * <p>Arity is the other half. {@code min} and {@code max} fold any number of arguments and the rest take exactly one,
 * so a unary function handed two must fail rather than quietly using the first.
 */
class FunctionsTest {

    // -- the list is exactly this ---------------------------------------------------------------------------

    @Test
    void everyNameTheEvaluatorOffersIsRecognised() {
        assertThat(List.of("min", "max", "abs", "floor", "ceil", "round", "sqrt"))
                .allMatch(Functions::isFunction);
    }

    /**
     * A name outside the list is not a function, whatever else it may be on the platform. The parser leans on this to
     * reject an identifier before it ever reaches {@code apply}, which is what keeps the surface closed.
     */
    @Test
    void aNameOutsideTheListIsNotAFunction() {
        assertThat(Functions.isFunction("exec")).isFalse();
        assertThat(Functions.isFunction("random")).isFalse();
        assertThat(Functions.isFunction("valueOf")).isFalse();
        assertThat(Functions.isFunction("")).isFalse();
        assertThat(Functions.isFunction("MIN")).as("the list is case sensitive").isFalse();
    }

    @Test
    void applyingANameOutsideTheListNamesItRatherThanFallingThrough() {
        assertThatThrownBy(() -> Functions.apply("exec", List.of(1d)))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("unknown function")
                .hasMessageContaining("exec");
    }

    // -- what each one computes -----------------------------------------------------------------------------

    @Test
    void theUnaryFunctionsComputeWhatTheirNamesSay() throws ExpressionException {
        assertThat(Functions.apply("abs", List.of(-3d))).isEqualTo(3d);
        assertThat(Functions.apply("floor", List.of(2.7d))).isEqualTo(2d);
        assertThat(Functions.apply("ceil", List.of(2.1d))).isEqualTo(3d);
        assertThat(Functions.apply("round", List.of(2.5d))).isEqualTo(3d);
        assertThat(Functions.apply("sqrt", List.of(9d))).isEqualTo(3d);
    }

    /** Rounding a negative half goes up, the way Math.round does, rather than away from zero. */
    @Test
    void roundingANegativeHalfGoesTowardsZero() throws ExpressionException {
        assertThat(Functions.apply("round", List.of(-2.5d))).isEqualTo(-2d);
    }

    @Test
    void floorAndCeilOfANegativeGoTheirOwnWays() throws ExpressionException {
        assertThat(Functions.apply("floor", List.of(-2.1d))).isEqualTo(-3d);
        assertThat(Functions.apply("ceil", List.of(-2.9d))).isEqualTo(-2d);
    }

    // -- the variadic pair ----------------------------------------------------------------------------------

    @Test
    void minAndMaxFoldEveryArgumentRatherThanTheFirstTwo() throws ExpressionException {
        assertThat(Functions.apply("min", List.of(5d, 2d, 9d, 1d))).isEqualTo(1d);
        assertThat(Functions.apply("max", List.of(5d, 2d, 9d, 1d))).isEqualTo(9d);
    }

    /** One argument is a legal fold: it is its own minimum, which is what makes the pair variadic rather than binary. */
    @Test
    void oneArgumentIsItsOwnMinimumAndMaximum() throws ExpressionException {
        assertThat(Functions.apply("min", List.of(4d))).isEqualTo(4d);
        assertThat(Functions.apply("max", List.of(4d))).isEqualTo(4d);
    }

    @Test
    void aFoldOverNothingSaysItNeedsAnArgument() {
        assertThatThrownBy(() -> Functions.apply("min", List.of()))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("min needs at least one argument");
    }

    // -- arity ----------------------------------------------------------------------------------------------

    /** A unary function handed two arguments fails rather than quietly using the first and dropping the second. */
    @Test
    void aUnaryFunctionRefusesASecondArgumentRatherThanIgnoringIt() {
        assertThatThrownBy(() -> Functions.apply("abs", List.of(-3d, 9d)))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("abs needs exactly one argument");
    }

    @Test
    void aUnaryFunctionRefusesNoArgumentsAtAll() {
        assertThatThrownBy(() -> Functions.apply("floor", List.of()))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("floor needs exactly one argument");
    }

    /** sqrt checks its arity through the same path as the others, so its message names sqrt and not something else. */
    @Test
    void sqrtReportsItsOwnNameWhenItsArityIsWrong() {
        assertThatThrownBy(() -> Functions.apply("sqrt", List.of(1d, 2d)))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("sqrt needs exactly one argument");
    }

    /**
     * The root of a negative is refused by name rather than left to produce NaN. The finite guard downstream would
     * catch the NaN, but "sqrt of a negative number" tells an operator which sub-expression to look at.
     */
    @Test
    void theRootOfANegativeIsRefusedByNameRatherThanYieldingNotANumber() {
        assertThatThrownBy(() -> Functions.apply("sqrt", List.of(-1d)))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("sqrt of a negative number");
    }

    @Test
    void theRootOfZeroIsZeroAndNotARefusal() throws ExpressionException {
        assertThat(Functions.apply("sqrt", List.of(0d))).isZero();
    }
}

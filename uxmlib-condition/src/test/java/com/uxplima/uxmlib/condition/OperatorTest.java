package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OperatorTest {

    @Test
    void symbolsRoundTrip() {
        for (Operator operator : Operator.values()) {
            assertThat(Operator.fromSymbol(operator.symbol())).isEqualTo(operator);
        }
    }

    @Test
    void orderingOperatorsAreFlaggedAsOrdering() {
        assertThat(Operator.GREATER.isOrdering()).isTrue();
        assertThat(Operator.GREATER_OR_EQUAL.isOrdering()).isTrue();
        assertThat(Operator.LESS.isOrdering()).isTrue();
        assertThat(Operator.LESS_OR_EQUAL.isOrdering()).isTrue();
        assertThat(Operator.EQUAL.isOrdering()).isFalse();
        assertThat(Operator.NOT_EQUAL.isOrdering()).isFalse();
    }

    @Test
    void twoCharOperatorsRankBeforeTheirOneCharPrefixes() {
        // a longest-match parser must see >= and <= before > and <
        int geIndex = Operator.bySymbolLengthDescending().indexOf(Operator.GREATER_OR_EQUAL);
        int gtIndex = Operator.bySymbolLengthDescending().indexOf(Operator.GREATER);
        int leIndex = Operator.bySymbolLengthDescending().indexOf(Operator.LESS_OR_EQUAL);
        int ltIndex = Operator.bySymbolLengthDescending().indexOf(Operator.LESS);
        assertThat(geIndex).isLessThan(gtIndex);
        assertThat(leIndex).isLessThan(ltIndex);
    }

    @Test
    void unknownSymbolRejected() {
        assertThatThrownBy(() -> Operator.fromSymbol("~="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown operator");
    }

    @Test
    void newStringOperatorsRoundTripBySymbol() {
        assertThat(Operator.fromSymbol("?=")).isEqualTo(Operator.CONTAINS);
        assertThat(Operator.fromSymbol("*")).isEqualTo(Operator.WILDCARD);
        assertThat(Operator.fromSymbol("||")).isEqualTo(Operator.OR);
    }

    @Test
    void newStringOperatorsAreNotOrdering() {
        assertThat(Operator.CONTAINS.isOrdering()).isFalse();
        assertThat(Operator.WILDCARD.isOrdering()).isFalse();
        assertThat(Operator.OR.isOrdering()).isFalse();
    }
}

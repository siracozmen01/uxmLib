package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The money condition reads its balance through the injected {@link Wallet} and compares it with the resolved
 * amount, so it never names an economy plugin. A map-backed wallet stands in for one.
 */
class MoneyConditionTest {

    private static OperandResolver mapResolver(Map<String, String> values) {
        return (player, template) -> values.getOrDefault(template, template);
    }

    private static ConditionRequest requestWith(Wallet wallet, OperandResolver resolver) {
        return ConditionRequest.builder(resolver).wallet(wallet).build();
    }

    @Test
    void passesWhenTheBalanceCoversTheAmount() {
        MoneyCondition condition = MoneyCondition.atLeast("coins", "100");
        ConditionRequest request = requestWith(new FakeWallet("coins", 150), OperandResolver.identity());
        assertThat(condition.test(request)).isTrue();
    }

    @Test
    void failsWhenTheBalanceFallsShort() {
        MoneyCondition condition = MoneyCondition.atLeast("coins", "100");
        ConditionRequest request = requestWith(new FakeWallet("coins", 99.5), OperandResolver.identity());
        assertThat(condition.test(request)).isFalse();
    }

    @Test
    void anEmptyCurrencyNamesTheWalletsDefaultPool() {
        MoneyCondition condition = MoneyCondition.parse(">= 100");
        assertThat(condition.currencyTemplate()).isEmpty();
        ConditionRequest request = requestWith(new FakeWallet("", 250), OperandResolver.identity());
        assertThat(condition.test(request)).isTrue();
    }

    @Test
    void aDifferentCurrencyIsADifferentPool() {
        MoneyCondition condition = MoneyCondition.atLeast("gems", "1");
        ConditionRequest request = requestWith(new FakeWallet("coins", 5000), OperandResolver.identity());
        assertThat(condition.test(request)).isFalse();
    }

    @Test
    void bothSidesResolveThroughTheOperandResolver() {
        MoneyCondition condition = MoneyCondition.atLeast("%pool%", "%price%");
        OperandResolver resolver = mapResolver(Map.of("%pool%", "coins", "%price%", "40"));
        assertThat(condition.test(requestWith(new FakeWallet("coins", 40), resolver)))
                .isTrue();
        assertThat(condition.test(requestWith(new FakeWallet("coins", 39), resolver)))
                .isFalse();
    }

    @Test
    void parseKeepsTheCurrencyAndAmountVerbatim() {
        MoneyCondition condition = MoneyCondition.parse("coins >= %price%");
        assertThat(condition.currencyTemplate()).isEqualTo("coins");
        assertThat(condition.operator()).isEqualTo(Operator.GREATER_OR_EQUAL);
        assertThat(condition.amountTemplate()).isEqualTo("%price%");
    }

    @Test
    void everyOperatorIsAvailableNotOnlyAtLeast() {
        ConditionRequest request = requestWith(new FakeWallet("coins", 0), OperandResolver.identity());
        assertThat(MoneyCondition.parse("coins == 0").test(request)).isTrue();
        assertThat(MoneyCondition.parse("coins < 10").test(request)).isTrue();
        assertThat(MoneyCondition.parse("coins > 10").test(request)).isFalse();
    }

    @Test
    void theDefaultWalletOnARequestReadsZero() {
        ConditionRequest request =
                ConditionRequest.builder(OperandResolver.identity()).build();
        assertThat(MoneyCondition.atLeast("coins", "1").test(request)).isFalse();
        assertThat(MoneyCondition.parse("coins == 0").test(request)).isTrue();
    }

    @Test
    void anAmountThatDoesNotResolveToANumberNeverPasses() {
        MoneyCondition condition = MoneyCondition.atLeast("coins", "%price%");
        ConditionRequest request = requestWith(new FakeWallet("coins", 1000), OperandResolver.identity());
        assertThat(condition.test(request)).isFalse();
    }

    @Test
    void anExpressionWithNoOperatorIsRejected() {
        assertThatThrownBy(() -> MoneyCondition.parse("coins 100"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no comparison operator");
    }
}

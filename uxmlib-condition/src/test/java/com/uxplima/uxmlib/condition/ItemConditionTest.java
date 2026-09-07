package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The item condition counts through the injected {@link ItemStore} and compares that count with the resolved
 * amount, so it fixes no item vocabulary of its own. A map-backed store stands in for an inventory.
 */
class ItemConditionTest {

    private static OperandResolver mapResolver(Map<String, String> values) {
        return (player, template) -> values.getOrDefault(template, template);
    }

    private static ConditionRequest requestWith(ItemStore store, OperandResolver resolver) {
        return ConditionRequest.builder(resolver).itemStore(store).build();
    }

    @Test
    void passesWhenTheCountCoversTheAmount() {
        ItemCondition condition = ItemCondition.atLeast("diamond", "3");
        assertThat(condition.test(requestWith(new FakeItemStore("diamond", 3), OperandResolver.identity())))
                .isTrue();
    }

    @Test
    void failsWhenTheCountFallsShort() {
        ItemCondition condition = ItemCondition.atLeast("diamond", "3");
        assertThat(condition.test(requestWith(new FakeItemStore("diamond", 2), OperandResolver.identity())))
                .isFalse();
    }

    @Test
    void anUnheldItemCountsZero() {
        ItemCondition condition = ItemCondition.atLeast("emerald", "1");
        assertThat(condition.test(requestWith(new FakeItemStore("diamond", 64), OperandResolver.identity())))
                .isFalse();
    }

    @Test
    void bothSidesResolveThroughTheOperandResolver() {
        ItemCondition condition = ItemCondition.atLeast("%token%", "%count%");
        OperandResolver resolver = mapResolver(Map.of("%token%", "diamond", "%count%", "5"));
        assertThat(condition.test(requestWith(new FakeItemStore("diamond", 5), resolver)))
                .isTrue();
        assertThat(condition.test(requestWith(new FakeItemStore("diamond", 4), resolver)))
                .isFalse();
    }

    @Test
    void parseKeepsTheItemAndAmountVerbatim() {
        ItemCondition condition = ItemCondition.parse("diamond >= %count%");
        assertThat(condition.itemTemplate()).isEqualTo("diamond");
        assertThat(condition.operator()).isEqualTo(Operator.GREATER_OR_EQUAL);
        assertThat(condition.amountTemplate()).isEqualTo("%count%");
    }

    @Test
    void everyOperatorIsAvailableNotOnlyAtLeast() {
        ConditionRequest request = requestWith(new FakeItemStore("diamond", 0), OperandResolver.identity());
        assertThat(ItemCondition.parse("diamond == 0").test(request)).isTrue();
        assertThat(ItemCondition.parse("diamond > 0").test(request)).isFalse();
    }

    @Test
    void theDefaultStoreOnARequestCountsZero() {
        ConditionRequest request =
                ConditionRequest.builder(OperandResolver.identity()).build();
        assertThat(ItemCondition.atLeast("diamond", "1").test(request)).isFalse();
    }

    @Test
    void anAmountThatDoesNotResolveToANumberNeverPasses() {
        ItemCondition condition = ItemCondition.atLeast("diamond", "%count%");
        assertThat(condition.test(requestWith(new FakeItemStore("diamond", 64), OperandResolver.identity())))
                .isFalse();
    }

    @Test
    void anExpressionWithNoOperatorIsRejected() {
        assertThatThrownBy(() -> ItemCondition.parse("diamond 3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no comparison operator");
    }
}

package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The placeholder condition resolves both operand templates through the injected resolver, then applies the
 * comparison. A fake map-backed resolver stands in for a PlaceholderAPI bridge so the test stays pure.
 */
class PlaceholderConditionTest {

    /** A resolver that looks templates up in a map, leaving anything unmapped (e.g. literals) untouched. */
    private static OperandResolver mapResolver(Map<String, String> values) {
        return (player, template) -> values.getOrDefault(template, template);
    }

    private static ConditionRequest requestWith(OperandResolver resolver) {
        return ConditionRequest.builder(resolver).build();
    }

    @Test
    void resolvesBothTemplatesThenCompares() {
        Map<String, String> values = new HashMap<>();
        values.put("%player_health%", "18");
        PlaceholderCondition condition = PlaceholderCondition.of("%player_health%", Operator.GREATER_OR_EQUAL, "10");
        assertThat(condition.test(requestWith(mapResolver(values)))).isTrue();
    }

    @Test
    void failsWhenResolvedValueDoesNotSatisfy() {
        Map<String, String> values = new HashMap<>();
        values.put("%player_health%", "4");
        PlaceholderCondition condition = PlaceholderCondition.of("%player_health%", Operator.GREATER_OR_EQUAL, "10");
        assertThat(condition.test(requestWith(mapResolver(values)))).isFalse();
    }

    @Test
    void bothSidesCanBePlaceholders() {
        Map<String, String> values = new HashMap<>();
        values.put("%player_level%", "30");
        values.put("%required_level%", "25");
        PlaceholderCondition condition =
                PlaceholderCondition.of("%player_level%", Operator.GREATER, "%required_level%");
        assertThat(condition.test(requestWith(mapResolver(values)))).isTrue();
    }

    @Test
    void stringPlaceholderEqualityWorks() {
        Map<String, String> values = new HashMap<>();
        values.put("%player_world%", "world_nether");
        PlaceholderCondition condition = PlaceholderCondition.of("%player_world%", Operator.EQUAL, "world_nether");
        assertThat(condition.test(requestWith(mapResolver(values)))).isTrue();
    }

    @Test
    void parseKeepsTemplatesVerbatimAndResolvesPerRequest() {
        PlaceholderCondition condition = PlaceholderCondition.parse("%vault_eco_balance% > 100");
        assertThat(condition.leftTemplate()).isEqualTo("%vault_eco_balance%");
        assertThat(condition.rightTemplate()).isEqualTo("100");
        assertThat(condition.operator()).isEqualTo(Operator.GREATER);

        Map<String, String> rich = new HashMap<>();
        rich.put("%vault_eco_balance%", "250");
        assertThat(condition.test(requestWith(mapResolver(rich)))).isTrue();

        Map<String, String> poor = new HashMap<>();
        poor.put("%vault_eco_balance%", "50");
        assertThat(condition.test(requestWith(mapResolver(poor)))).isFalse();
    }

    @Test
    void identityResolverComparesLiterals() {
        PlaceholderCondition condition = PlaceholderCondition.of("5", Operator.LESS, "10");
        assertThat(condition.test(requestWith(OperandResolver.identity()))).isTrue();
    }

    @Test
    void parseKeepsAComparisonOperatorInsideAPlaceholderOutOfTheSplit() {
        // %math_2<3% must stay whole; the real operator is the '==' after it, not the '<' in its body.
        PlaceholderCondition condition = PlaceholderCondition.parse("%math_2<3% == true");
        assertThat(condition.leftTemplate()).isEqualTo("%math_2<3%");
        assertThat(condition.rightTemplate()).isEqualTo("true");
        assertThat(condition.operator()).isEqualTo(Operator.EQUAL);

        Map<String, String> values = new HashMap<>();
        values.put("%math_2<3%", "true");
        assertThat(condition.test(requestWith(mapResolver(values)))).isTrue();
    }
}

package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The match rules a drag spec carries. The record holds no logic of its own, so the only thing it can get wrong is
 * letting a caller change what a loaded spec matches after the spec is loaded. A menu spec is parsed once and read on
 * every click, so a mutable list inside one is a rule that can change under a running server.
 */
class ItemRuleSpecTest {

    @Test
    void theMaterialsAreCopiedSoALaterEditByTheCallerDoesNotChangeWhatMatches() {
        List<String> source = new ArrayList<>(List.of("DIAMOND"));
        ItemRuleSpec rule = new ItemRuleSpec(source, 1, "");

        source.add("BEDROCK");

        assertThat(rule.materials()).containsExactly("DIAMOND");
    }

    @Test
    void theCopyIsUnmodifiableSoAReaderCannotEditItEither() {
        ItemRuleSpec rule = new ItemRuleSpec(new ArrayList<>(List.of("DIAMOND")), 1, "");

        assertThatThrownBy(() -> rule.materials().add("BEDROCK")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anEmptyWhitelistIsARuleAndNotAMissingOne() {
        ItemRuleSpec rule = new ItemRuleSpec(List.of(), 0, "");

        assertThat(rule.materials()).isEmpty();
        assertThat(rule.nameContains()).isEmpty();
        assertThat(rule.minAmount()).isZero();
    }

    @Test
    void theRecordCarriesTheValuesItWasGiven() {
        ItemRuleSpec rule = new ItemRuleSpec(List.of("DIAMOND", "EMERALD"), 16, "rare");

        assertThat(rule.materials()).containsExactly("DIAMOND", "EMERALD");
        assertThat(rule.minAmount()).isEqualTo(16);
        assertThat(rule.nameContains()).isEqualTo("rare");
    }

    @Test
    void twoRulesWrittenTheSameWayAreTheSameRule() {
        assertThat(new ItemRuleSpec(List.of("DIAMOND"), 1, "rare"))
                .isEqualTo(new ItemRuleSpec(List.of("DIAMOND"), 1, "rare"))
                .hasSameHashCodeAs(new ItemRuleSpec(List.of("DIAMOND"), 1, "rare"));
    }
}

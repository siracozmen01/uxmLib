package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class ValueOrPlaceholderTest {

    // A fake resolver standing in for a real PAPI/placeholder engine: it just looks names up in a map.
    private static final Function<String, String> RESOLVER = Map.of("player", "Steve", "balance", "1000")::get;

    @Test
    void aLiteralResolvesToItselfWithoutTouchingTheResolver() {
        AtomicInteger calls = new AtomicInteger();
        Function<String, String> spy = name -> {
            calls.incrementAndGet();
            return name;
        };
        ValueOrPlaceholder<String> literal = ValueOrPlaceholder.literal("fixed");

        assertThat(literal.resolve(spy)).isEqualTo("fixed");
        assertThat(literal.isPlaceholder()).isFalse();
        assertThat(calls).hasValue(0);
    }

    @Test
    void aPlaceholderResolvesThroughTheInjectedResolver() {
        ValueOrPlaceholder<String> placeholder = ValueOrPlaceholder.placeholder("player");

        assertThat(placeholder.resolve(RESOLVER)).isEqualTo("Steve");
        assertThat(placeholder.isPlaceholder()).isTrue();
        assertThat(placeholder.template()).contains("player");
    }

    @Test
    void resolvesLazilyOnlyWhenAsked() {
        AtomicInteger calls = new AtomicInteger();
        Function<String, String> spy = name -> {
            calls.incrementAndGet();
            return "v" + calls.get();
        };
        ValueOrPlaceholder<String> placeholder = ValueOrPlaceholder.placeholder("k");

        assertThat(calls).hasValue(0);
        assertThat(placeholder.resolve(spy)).isEqualTo("v1");
        assertThat(placeholder.resolve(spy)).isEqualTo("v2");
        assertThat(calls).hasValue(2);
    }

    @Test
    void carriesAnyValueType() {
        ValueOrPlaceholder<Integer> literal = ValueOrPlaceholder.literal(7);
        Function<String, Integer> resolver = key -> 42;

        assertThat(literal.resolve(resolver)).isEqualTo(7);
        assertThat(ValueOrPlaceholder.<Integer>placeholder("any").resolve(resolver))
                .isEqualTo(42);
    }

    @Test
    void aLiteralHasNoTemplate() {
        assertThat(ValueOrPlaceholder.literal("x").template()).isEmpty();
    }

    @Test
    void equalsDistinguishesLiteralsFromPlaceholders() {
        assertThat(ValueOrPlaceholder.literal("a")).isEqualTo(ValueOrPlaceholder.literal("a"));
        assertThat(ValueOrPlaceholder.placeholder("a")).isEqualTo(ValueOrPlaceholder.placeholder("a"));
        assertThat(ValueOrPlaceholder.literal("a")).isNotEqualTo(ValueOrPlaceholder.placeholder("a"));
    }

    @Test
    void rejectsABlankPlaceholderTemplate() {
        assertThatThrownBy(() -> ValueOrPlaceholder.placeholder("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/**
 * Covers the typed {@link ErrorContext}: a per-argument failure carries which argument failed, the raw input
 * the sender gave, and why, and renders to a clear Adventure message naming the argument and the input. The
 * record and its rendering are pure, so they are exercised directly.
 */
class ErrorContextTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void namesTheArgumentTheRawInputAndTheReason() {
        ErrorContext error = new ErrorContext("amount", "abc", "not a number");
        String rendered = plain(error.toComponent());
        assertThat(rendered).contains("amount").contains("abc").contains("not a number");
    }

    @Test
    void aFailureWithoutAReasonStillNamesTheArgument() {
        ErrorContext error = new ErrorContext("target", "Steve", "");
        String rendered = plain(error.toComponent());
        assertThat(rendered).contains("target").contains("Steve");
    }

    @Test
    void anArgumentResolveExceptionCarriesItsContext() {
        ErrorContext error = new ErrorContext("page", "-1", "must be at least 1");
        ArgumentResolveException thrown = new ArgumentResolveException(error);
        assertThat(thrown.context()).isSameAs(error);
        assertThat(thrown.getMessage()).contains("page").contains("must be at least 1");
    }
}

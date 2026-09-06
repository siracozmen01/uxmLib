package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/** Who owns the cancel word: this class by default, and nobody when the caller says so. */
class PlayerInputCancelKeywordTest {

    @Test
    void aRouterWithAKeywordTurnsThatLineIntoACancellation() {
        assertThat(submit(new InputRouter("cancel"), "cancel")).isInstanceOf(InputResult.Cancelled.class);
    }

    @Test
    void theKeywordIsCaseInsensitive() {
        assertThat(submit(new InputRouter("cancel"), "CaNcEl")).isInstanceOf(InputResult.Cancelled.class);
    }

    /**
     * The point of the no-keyword mode. A seam that reads an operator's own cancel-word list has to be the
     * only floor that applies one, or a word the operator deliberately left out still cancels here and
     * nothing reports why.
     */
    @Test
    void aRouterWithNoKeywordSubmitsTheWordCancelLikeAnyOther() {
        InputResult result = submit(new InputRouter(null), "cancel");

        assertThat(result).isInstanceOf(InputResult.Submitted.class);
        assertThat(((InputResult.Submitted) result).text()).isEqualTo("cancel");
    }

    /** Absent and blank are different tests: an empty keyword must not make an empty line a cancellation. */
    @Test
    void anEmptyKeywordIsNotTheSameAsNoKeyword() {
        assertThat(submit(new InputRouter(""), "")).isInstanceOf(InputResult.Cancelled.class);
        assertThat(submit(new InputRouter(null), "")).isInstanceOf(InputResult.Submitted.class);
    }

    private static InputResult submit(InputRouter router, String line) {
        UUID id = UUID.randomUUID();
        AtomicReference<InputResult> seen = new AtomicReference<>();
        router.register(id, InputType.CHAT, seen::set);
        router.submit(id, line);
        return Objects.requireNonNull(seen.get(), "the router did not dispatch a result");
    }
}

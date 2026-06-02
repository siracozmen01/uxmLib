package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The router is the pure per-player state machine behind {@link PlayerInput}: it tracks which player is
 * awaiting which backend, applies the cancel keyword, sanitizes the captured line, and dispatches the
 * one-shot result. It has no Bukkit dependency, so every branch is unit-testable.
 */
class InputRouterTest {

    @Test
    void awaitingReflectsTheRegisteredBackend() {
        InputRouter router = new InputRouter("cancel");
        UUID id = UUID.randomUUID();

        assertThat(router.awaiting(id)).isEmpty();
        router.register(id, InputType.CHAT, r -> {});
        assertThat(router.awaiting(id)).contains(InputType.CHAT);
    }

    @Test
    void submitDispatchesTypedTextAndClearsPending() {
        InputRouter router = new InputRouter("cancel");
        UUID id = UUID.randomUUID();
        List<InputResult> results = new ArrayList<>();
        router.register(id, InputType.CHAT, results::add);

        boolean consumed = router.submit(id, "a name");

        assertThat(consumed).isTrue();
        assertThat(results).containsExactly(new InputResult.Submitted("a name"));
        assertThat(router.awaiting(id)).isEmpty();
    }

    @Test
    void cancelKeywordYieldsCancelled() {
        InputRouter router = new InputRouter("cancel");
        UUID id = UUID.randomUUID();
        List<InputResult> results = new ArrayList<>();
        router.register(id, InputType.CHAT, results::add);

        router.submit(id, "Cancel"); // case-insensitive

        assertThat(results).containsExactly(InputResult.Cancelled.INSTANCE);
        assertThat(router.awaiting(id)).isEmpty();
    }

    @Test
    void submitForAnUnknownPlayerIsNotConsumed() {
        InputRouter router = new InputRouter("cancel");

        assertThat(router.submit(UUID.randomUUID(), "x")).isFalse();
    }

    @Test
    void controlCharactersAndSectionSignsAreStripped() {
        InputRouter router = new InputRouter("cancel");
        UUID id = UUID.randomUUID();
        List<InputResult> results = new ArrayList<>();
        router.register(id, InputType.SIGN, results::add);

        // The section sign and the BEL/tab control chars are dropped; other text stays verbatim.
        router.submit(id, "he§cllo world\t");

        assertThat(results).containsExactly(new InputResult.Submitted("hecllo world"));
    }

    @Test
    void cancelDispatchesCancelledOnce() {
        InputRouter router = new InputRouter("cancel");
        UUID id = UUID.randomUUID();
        List<InputResult> results = new ArrayList<>();
        router.register(id, InputType.SIGN, results::add);

        assertThat(router.cancel(id)).isTrue();
        assertThat(router.cancel(id)).isFalse(); // already done; no second dispatch
        assertThat(results).containsExactly(InputResult.Cancelled.INSTANCE);
    }

    @Test
    void forgetDropsPendingWithoutDispatch() {
        InputRouter router = new InputRouter("cancel");
        UUID id = UUID.randomUUID();
        List<InputResult> results = new ArrayList<>();
        router.register(id, InputType.ANVIL, results::add);

        router.forget(id);

        assertThat(router.awaiting(id)).isEmpty();
        assertThat(results).isEmpty();
    }

    @Test
    void registeringTwiceCancelsTheEarlierPending() {
        InputRouter router = new InputRouter("cancel");
        UUID id = UUID.randomUUID();
        List<InputResult> first = new ArrayList<>();
        router.register(id, InputType.CHAT, first::add);
        router.register(id, InputType.SIGN, r -> {});

        // The superseded request resolves as cancelled so its caller is never left hanging.
        assertThat(first).containsExactly(InputResult.Cancelled.INSTANCE);
        assertThat(router.awaiting(id)).contains(InputType.SIGN);
    }
}

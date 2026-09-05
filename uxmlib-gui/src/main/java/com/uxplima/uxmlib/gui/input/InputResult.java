package com.uxplima.uxmlib.gui.input;

import java.util.Objects;

/**
 * The outcome of a {@link PlayerInput} prompt, the same shape regardless of backend: the player either
 * {@link Submitted submitted} a line of text or {@link Cancelled cancelled} (typed the cancel keyword, when
 * the prompt was given one; closed the prompt; disconnected; or the backend could not be opened).
 *
 * <p>Whether a typed line can be a cancellation therefore depends on how the {@link PlayerInput} was built.
 * One made with {@link PlayerInput#withoutCancelKeyword} never turns a typed line into {@link Cancelled}, so
 * a caller that owns its own cancel words sees every line as a {@link Submitted} and decides for itself.
 */
public sealed interface InputResult permits InputResult.Submitted, InputResult.Cancelled {

    /** The player submitted {@code text} (already sanitized; possibly empty). */
    record Submitted(String text) implements InputResult {
        public Submitted {
            Objects.requireNonNull(text, "text");
        }
    }

    /** The prompt was dismissed without a submission. */
    record Cancelled() implements InputResult {
        public static final Cancelled INSTANCE = new Cancelled();
    }
}

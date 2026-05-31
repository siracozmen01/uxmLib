package com.uxplima.uxmlib.gui.anvil;

import java.util.Objects;

/**
 * The outcome of an anvil text prompt: the player either {@link Submitted submitted} text or
 * {@link Cancelled cancelled} (closed the anvil, or it could not be opened).
 */
public sealed interface AnvilResult permits AnvilResult.Submitted, AnvilResult.Cancelled {

    /** The player clicked the result slot; {@code text} is what they typed (possibly empty). */
    record Submitted(String text) implements AnvilResult {
        public Submitted {
            Objects.requireNonNull(text, "text");
        }
    }

    /** The prompt was dismissed without a submission. */
    record Cancelled() implements AnvilResult {
        public static final Cancelled INSTANCE = new Cancelled();
    }
}

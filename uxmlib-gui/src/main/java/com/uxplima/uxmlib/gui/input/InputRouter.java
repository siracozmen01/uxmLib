package com.uxplima.uxmlib.gui.input;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The per-player pending-input state machine behind {@link PlayerInput}, with no Bukkit dependency so every
 * branch is unit-testable. It records which player is awaiting which backend, applies the cancel keyword,
 * sanitizes captured lines, and dispatches each result exactly once.
 *
 * <p>State lives on the instance (a per-player map), never statically. The dispatch guard is an
 * {@link AtomicBoolean} per pending request so a late event (e.g. a quit racing a submit) cannot fire the
 * callback twice.
 */
final class InputRouter {

    private final String cancelKeyword;
    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();

    InputRouter(String cancelKeyword) {
        this.cancelKeyword = Objects.requireNonNull(cancelKeyword, "cancelKeyword");
    }

    /** Begin awaiting a line from {@code id} via {@code type}; any earlier pending request is cancelled. */
    void register(UUID id, InputType type, Consumer<InputResult> callback) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(callback, "callback");
        Pending previous = pending.put(id, new Pending(type, callback));
        if (previous != null) {
            previous.dispatch(InputResult.Cancelled.INSTANCE);
        }
    }

    /** The backend {@code id} is awaiting, or empty if it has no pending request. */
    Optional<InputType> awaiting(UUID id) {
        Objects.requireNonNull(id, "id");
        Pending current = pending.get(id);
        return current == null ? Optional.empty() : Optional.of(current.type);
    }

    /**
     * Feed a captured {@code line} for {@code id}. The cancel keyword (case-insensitive, after sanitizing)
     * yields {@link InputResult.Cancelled}; otherwise the sanitized text is submitted. Returns {@code true}
     * when a pending request was consumed, {@code false} when there was none.
     */
    boolean submit(UUID id, String line) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(line, "line");
        Pending current = pending.remove(id);
        if (current == null) {
            return false;
        }
        String clean = sanitize(line);
        InputResult result = clean.equalsIgnoreCase(cancelKeyword)
                ? InputResult.Cancelled.INSTANCE
                : new InputResult.Submitted(clean);
        current.dispatch(result);
        return true;
    }

    /** Resolve {@code id}'s pending request as cancelled. Returns whether a dispatch happened. */
    boolean cancel(UUID id) {
        Objects.requireNonNull(id, "id");
        Pending current = pending.remove(id);
        return current != null && current.dispatch(InputResult.Cancelled.INSTANCE);
    }

    /** Drop {@code id}'s pending request without dispatching (the backend owns its own callback). */
    void forget(UUID id) {
        Objects.requireNonNull(id, "id");
        pending.remove(id);
    }

    /** Strip control characters and section signs so backends never carry colour codes or newlines. */
    static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '§' && !Character.isISOControl(c)) {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    private static final class Pending {
        private final InputType type;
        private final Consumer<InputResult> callback;
        private final AtomicBoolean done = new AtomicBoolean(false);

        private Pending(InputType type, Consumer<InputResult> callback) {
            this.type = type;
            this.callback = callback;
        }

        private boolean dispatch(InputResult result) {
            if (done.compareAndSet(false, true)) {
                callback.accept(result);
                return true;
            }
            return false;
        }
    }
}

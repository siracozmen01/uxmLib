package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Trims a handler exception's stack trace down to the frames an operator cares about before it is logged by
 * the clean-error path. The interesting line is where the consumer's command method threw; the frames above
 * it — Brigadier dispatch, this library's reflective invoke, JDK reflection glue — are noise that pushes the
 * real cause off the top of a busy console. This drops the leading framework/reflection/JDK frames (and the
 * reflection {@link InvocationTargetException} wrapper) so the logged trace starts at the consumer's own code.
 *
 * <p><strong>Mutating, not pure:</strong> {@link #sanitize} rewrites the {@link Throwable}'s frames in place
 * via {@link Throwable#setStackTrace} and returns the same instance, so callers can log it as usual. Because
 * the rewrite is visible to any other observer of that throwable, only call it on a freshly-unwrapped
 * handler exception that is not otherwise held or re-logged — which is exactly the clean-error path's input
 * (the cause of an {@link InvocationTargetException} or a future's {@code CompletionException}, minted per
 * dispatch). A throwable whose every frame is framework noise keeps its original trace rather than being
 * blanked.
 */
final class StackTraceSanitizer {

    /** Class-name prefixes whose leading frames are dropped: this library, the JDK, and Brigadier dispatch. */
    private static final List<String> NOISE_PREFIXES = List.of(
            "com.uxplima.uxmlib.command.",
            "com.mojang.brigadier.",
            "jdk.internal.reflect.",
            "sun.reflect.",
            "java.lang.reflect.");

    private StackTraceSanitizer() {}

    /**
     * Strip the leading framework/reflection frames from {@code throwable} (and any reflection wrapper at the
     * top of the cause chain) in place, returning the same instance for fluent logging. A {@code null} input
     * is returned unchanged.
     */
    static @org.jspecify.annotations.Nullable Throwable sanitize(
            @org.jspecify.annotations.Nullable Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable target = unwrapReflection(throwable);
        target.setStackTrace(trimLeadingNoise(target.getStackTrace()));
        return target;
    }

    /** Unwrap a reflective {@link InvocationTargetException} so the real handler exception is what gets logged. */
    private static Throwable unwrapReflection(Throwable throwable) {
        Throwable cause = throwable.getCause();
        if (throwable instanceof InvocationTargetException && cause != null) {
            return cause;
        }
        return throwable;
    }

    /**
     * Drop the leading {@code frames} whose declaring class is framework/reflection noise; keep everything
     * from the first consumer frame onward. If every frame is noise the original array is returned, so a fully
     * synthetic trace is never blanked.
     */
    static StackTraceElement[] trimLeadingNoise(StackTraceElement[] frames) {
        Objects.requireNonNull(frames, "frames");
        int firstReal = 0;
        while (firstReal < frames.length && isNoise(frames[firstReal])) {
            firstReal++;
        }
        if (firstReal == 0 || firstReal == frames.length) {
            return frames;
        }
        List<StackTraceElement> kept = new ArrayList<>(List.of(frames).subList(firstReal, frames.length));
        return kept.toArray(StackTraceElement[]::new);
    }

    private static boolean isNoise(StackTraceElement frame) {
        String className = frame.getClassName();
        for (String prefix : NOISE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

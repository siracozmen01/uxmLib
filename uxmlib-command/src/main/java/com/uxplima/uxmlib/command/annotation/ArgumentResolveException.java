package com.uxplima.uxmlib.command.annotation;

import java.util.Objects;

/**
 * An {@link IllegalArgumentException} that carries a typed {@link ErrorContext} — which argument failed, the
 * raw input, and why. It rides the same clean-error path a bare {@code IllegalArgumentException} from a
 * resolver does, but {@link CommandExecutors} recognises it and renders the richer per-argument message
 * instead of a flat string, so the sender is told exactly which argument they got wrong.
 */
final class ArgumentResolveException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorContext context;

    ArgumentResolveException(ErrorContext context) {
        super(Objects.requireNonNull(context, "context").message());
        this.context = context;
    }

    ArgumentResolveException(ErrorContext context, Throwable cause) {
        super(Objects.requireNonNull(context, "context").message(), cause);
        this.context = context;
    }

    /** The typed context describing the per-argument failure. */
    ErrorContext context() {
        return context;
    }
}

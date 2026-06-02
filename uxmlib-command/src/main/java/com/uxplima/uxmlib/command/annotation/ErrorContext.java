package com.uxplima.uxmlib.command.annotation;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The typed context of a per-argument failure: which {@code argument} the sender got wrong, the raw
 * {@code input} they gave for it, and the {@code reason} it was rejected. Where a bare
 * {@link IllegalArgumentException} only carries a flat message, this names the failing argument so the reply
 * can point at it precisely ("Invalid value 'abc' for &lt;amount&gt;: not a number") and so a consumer can
 * localize the three parts independently. Rendered to an Adventure {@link Component} on the same clean-error
 * reply path a rejected argument already uses.
 *
 * @param argument the argument name as declared on its {@code @Arg}
 * @param input the raw text the sender supplied for it
 * @param reason why it was rejected; may be empty when the resolver gave no detail
 */
record ErrorContext(String argument, String input, String reason) {

    ErrorContext {
        Objects.requireNonNull(argument, "argument");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(reason, "reason");
    }

    /** A red message naming the bad input and the failing argument, with the reason appended when present. */
    Component toComponent() {
        Component message = Component.text("Invalid value '" + input + "' for <" + argument + ">", NamedTextColor.RED);
        if (reason.isEmpty()) {
            return message;
        }
        return message.append(Component.text(": " + reason, NamedTextColor.RED));
    }

    /** The flat single-line form of {@link #toComponent()}, for an exception message or a log line. */
    String message() {
        String head = "Invalid value '" + input + "' for <" + argument + ">";
        return reason.isEmpty() ? head : head + ": " + reason;
    }
}

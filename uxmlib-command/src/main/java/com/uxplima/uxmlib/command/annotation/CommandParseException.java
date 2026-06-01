package com.uxplima.uxmlib.command.annotation;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * Thrown when an annotated command handler is malformed (e.g. an unsupported parameter type, a missing
 * {@link Arg} name, or a bad literal path). Raised at registration time, not at command-run time, so
 * mistakes surface on plugin enable rather than when a player types the command.
 */
public final class CommandParseException extends RuntimeException {

    public CommandParseException(String message) {
        super(message);
    }

    public CommandParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

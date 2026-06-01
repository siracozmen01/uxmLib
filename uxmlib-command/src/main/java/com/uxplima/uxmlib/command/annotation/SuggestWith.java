package com.uxplima.uxmlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * Drives tab-completion for an {@code @}{@link Arg} parameter from a {@link SuggestionSource} class, for
 * completions that depend on the sender or earlier arguments. The class must have a public no-argument
 * constructor; it is instantiated once at registration, not per keystroke.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface SuggestWith {

    /** The provider class supplying completions. */
    Class<? extends SuggestionSource> value();
}

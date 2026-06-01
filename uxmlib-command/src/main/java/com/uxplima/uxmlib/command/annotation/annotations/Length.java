package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bounds the character length of a {@code String} {@code @}{@link Arg} parameter to an inclusive range.
 * Brigadier has no native string-length argument type, so the bound is enforced server-side by a validator
 * after the word (or greedy string) is parsed; an out-of-range value is rejected with a clear message on the
 * same clean-error path a bad argument uses. Applies to {@code String} parameters; ignored on other types.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Length {

    /** Inclusive minimum number of characters. */
    int min() default 0;

    /** Inclusive maximum number of characters. */
    int max() default Integer.MAX_VALUE;
}

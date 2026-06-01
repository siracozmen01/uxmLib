package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bounds a numeric {@code @}{@link Arg} parameter to an inclusive range. The bounds are applied two ways:
 * the native Brigadier argument type is built with them so the client rejects an out-of-range value before
 * it is even sent, and a server-side validator re-checks the resolved value as defence in depth (a crafted
 * packet or a non-Brigadier dispatch path cannot slip past). Applies to {@code int}/{@code long}/
 * {@code double}/{@code float} parameters; ignored on other types. Prefer this over {@code @Arg(min=, max=)}
 * for new code — the older inline bounds remain as a thin alias.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Range {

    /** Inclusive minimum. */
    double min() default Double.NEGATIVE_INFINITY;

    /** Inclusive maximum. */
    double max() default Double.POSITIVE_INFINITY;
}

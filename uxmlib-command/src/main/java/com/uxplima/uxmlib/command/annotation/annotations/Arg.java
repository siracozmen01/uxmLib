package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a command argument parameter. The parameter's Java type drives the Brigadier argument type
 * (String, int, double, boolean are built in); the {@link #value()} is the argument name shown in usage
 * and used to read it back. Optional numeric bounds apply to {@code int}/{@code double} parameters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Arg {

    /** The argument name (e.g. {@code "amount"}). */
    String value();

    /** Inclusive minimum for a numeric argument; ignored for non-numeric types. */
    double min() default Double.NEGATIVE_INFINITY;

    /** Inclusive maximum for a numeric argument; ignored for non-numeric types. */
    double max() default Double.POSITIVE_INFINITY;

    /**
     * Whether this argument may be omitted. An optional argument fills {@link #def()} (or the type's zero
     * value) when absent. Only trailing arguments may be optional — validated at registration.
     */
    boolean optional() default false;

    /** The default value (parsed from this string) used for an {@link #optional()} argument when omitted. */
    String def() default "";

    /**
     * For a trailing {@code String} argument, consume the entire rest of the input (spaces included) rather
     * than a single word — e.g. a message or reason. Only the last argument may be greedy.
     */
    boolean greedy() default false;
}

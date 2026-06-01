package com.uxplima.uxmlib.command.annotation;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * A post-resolve check on a single argument value. After a {@link ParamResolver} parses an argument, every
 * validator registered for that value's Java type runs against the resolved value; a validator rejects bad
 * input by throwing an {@link IllegalArgumentException}, whose message is shown to the sender in red on the
 * same clean-error path a resolver rejection uses. Validators are how {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Range} and {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Length} enforce their bounds server-side in addition to
 * any native Brigadier bounds, and let a consumer add domain checks (e.g. "must be online &gt; 5 min")
 * without writing a whole resolver. Register one on a {@link ParamResolvers} registry with
 * {@link ParamResolvers#validate(Class, ParameterValidator)}.
 *
 * @param <T> the resolved value type this validator checks
 */
@FunctionalInterface
public interface ParameterValidator<T> {

    /**
     * Check {@code value} for the parameter described by {@code arg}. Return normally to accept; throw an
     * {@link IllegalArgumentException} with a sender-facing message to reject. {@code value} may be
     * {@code null} for an omitted optional argument that defaulted to no value.
     */
    void validate(@org.jspecify.annotations.Nullable T value, Arg arg);
}

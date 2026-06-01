package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Parameter;

import com.uxplima.uxmlib.command.annotation.annotations.Length;
import com.uxplima.uxmlib.command.annotation.annotations.Range;
import org.jspecify.annotations.Nullable;

/**
 * The built-in server-side checks for {@code @}{@link Range} and {@code @}{@link Length}. Brigadier already
 * rejects an out-of-range number before it reaches the server (the numeric argument type is built with the
 * bounds), but a non-Brigadier dispatch or a crafted packet could bypass that, and string length has no
 * native argument type at all. So after a value is resolved this re-checks it against the parameter's
 * annotations and throws an {@link IllegalArgumentException} — caught on the same clean-error path a rejected
 * argument uses — when it is out of bounds. Pure metadata read off the parameter; no behaviour of its own.
 */
final class ArgValidators {

    private ArgValidators() {}

    /** Re-check a resolved {@code value} against the {@code @Range}/{@code @Length} on {@code parameter}. */
    static void check(Parameter parameter, @Nullable Object value) {
        Range range = parameter.getAnnotation(Range.class);
        if (range != null && value instanceof Number number) {
            checkRange(parameter, number.doubleValue(), range);
        }
        Length length = parameter.getAnnotation(Length.class);
        if (length != null && value instanceof String text) {
            checkLength(parameter, text, length);
        }
    }

    /** The native lower bound to hand a numeric argument type: {@code @Range.min} wins, else {@code @Arg.min}. */
    static double lowerBound(Parameter parameter, double argMin) {
        Range range = parameter.getAnnotation(Range.class);
        return range != null && range.min() != Double.NEGATIVE_INFINITY ? range.min() : argMin;
    }

    /** The native upper bound to hand a numeric argument type: {@code @Range.max} wins, else {@code @Arg.max}. */
    static double upperBound(Parameter parameter, double argMax) {
        Range range = parameter.getAnnotation(Range.class);
        return range != null && range.max() != Double.POSITIVE_INFINITY ? range.max() : argMax;
    }

    private static void checkRange(Parameter parameter, double value, Range range) {
        if (value < range.min() || value > range.max()) {
            throw new IllegalArgumentException(
                    name(parameter) + " must be between " + format(range.min()) + " and " + format(range.max()));
        }
    }

    private static void checkLength(Parameter parameter, String text, Length length) {
        if (text.length() < length.min() || text.length() > length.max()) {
            throw new IllegalArgumentException(
                    name(parameter) + " must be between " + length.min() + " and " + length.max() + " characters");
        }
    }

    private static String name(Parameter parameter) {
        com.uxplima.uxmlib.command.annotation.annotations.Arg arg =
                parameter.getAnnotation(com.uxplima.uxmlib.command.annotation.annotations.Arg.class);
        return arg != null ? arg.value() : parameter.getName();
    }

    /** Render a bound without a trailing {@code .0} for whole numbers, and as {@code ∞} when unbounded. */
    private static String format(double bound) {
        if (bound == Double.NEGATIVE_INFINITY || bound == Double.POSITIVE_INFINITY) {
            return bound < 0 ? "-∞" : "∞";
        }
        return bound == Math.rint(bound) ? Long.toString((long) bound) : Double.toString(bound);
    }
}

package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an executable command branch. The {@link #value()} is the space-separated literal
 * path beneath the root {@link Command} (e.g. {@code "set"} or {@code "admin reload"}); an empty path
 * makes the method the root command's own executor. After the literals, the method's
 * {@link Arg}-annotated parameters become typed Brigadier arguments, and a leading {@code Sender} or
 * {@code CommandSourceStack} parameter is injected.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subcommand {

    /** The literal path beneath the root, space-separated; empty for the root executor. */
    String value() default "";

    /** A short description of this branch, shown in the generated help. */
    String description() default "";
}

package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a command on a permission node. On a {@link Subcommand} method it guards that branch; on a
 * {@link Command} class it guards every branch. The node becomes a Brigadier {@code requires} check.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Permission {

    /** The permission node the sender must hold. */
    String value();
}

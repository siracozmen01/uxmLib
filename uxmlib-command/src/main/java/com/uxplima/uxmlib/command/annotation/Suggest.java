package com.uxplima.uxmlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * Offers a fixed list of tab-completions for an {@code @}{@link Arg} parameter, overriding the argument
 * type's native suggestions. Use it for a small known set of literal completions that aren't worth a
 * dedicated resolver (e.g. {@code @Suggest({"on", "off"})}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Suggest {

    /** The completion strings to offer. */
    String[] value();
}

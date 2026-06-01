package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a {@code @}{@link Subcommand} (or every subcommand of a {@code @}{@link Command} class) to
 * players. The console and command blocks get a clean denial message instead of an error. A method that
 * injects a {@link org.bukkit.entity.Player} parameter is player-only automatically; use this when the
 * method has no such parameter but still must not run from console.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PlayerOnly {}

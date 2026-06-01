package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rate-limits a {@link Subcommand} branch per player. After a player runs the branch they must wait
 * {@link #value()} before running it again; an early attempt is vetoed with a message naming the time
 * still left. The window is keyed by the command path and the player's UUID, so different branches and
 * different players are independent, and the console (which has no UUID) is never gated. On a class it
 * applies to every branch.
 *
 * <p>The duration is the human form parsed by {@code com.uxplima.uxmlib.common.Durations} — for example
 * {@code "30s"}, {@code "1h30m"} or {@code "2d"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Cooldown {

    /** The wait between uses, as a human duration such as {@code "30s"} or {@code "1h30m"}. */
    String value();
}

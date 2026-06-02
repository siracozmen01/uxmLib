package com.uxplima.uxmlib.command.annotation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Breaks ties between overlapping {@link Subcommand} branches of the same {@link Command}. When two branches
 * could both consume the same input — say {@code /give <amount>} (an {@code int}) and {@code /give <name>}
 * (a {@code String}) — Brigadier tries the sibling argument nodes in attachment order and runs the first that
 * parses. A lower {@link #value()} is the higher priority and attaches first, so the branch you mark
 * {@code @CommandPriority(1)} wins over a sibling marked {@code @CommandPriority(5)} (or one left unmarked).
 *
 * <p>Branches without this annotation keep their natural ordering (longest literal path first, then by
 * priority); an unmarked branch is treated as the lowest priority, so any explicitly prioritised sibling is
 * tried ahead of it. This only matters where two branches genuinely overlap — distinct literal paths never
 * collide and need no priority. It is a deliberate escape hatch, not something most commands ever require.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandPriority {

    /** The priority value; a lower number is tried first. Branches without the annotation rank last. */
    int value();
}

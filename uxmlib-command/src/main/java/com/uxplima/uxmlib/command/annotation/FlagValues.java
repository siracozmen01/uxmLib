package com.uxplima.uxmlib.command.annotation;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;

/**
 * Parses a value flag's raw token through the same {@link ParamResolver} an {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Arg} would use, so a {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Flag} {@code Player p} resolves a player exactly like an
 * {@code @Arg Player p}. The token-through-a-throwaway-tree mechanics live in {@link TokenResolution}, shared
 * with the collection resolvers; this only adapts a flag's parse failure message. A parse failure surfaces as
 * an {@link IllegalArgumentException} on the clean-error path a bad argument uses.
 */
final class FlagValues {

    private FlagValues() {}

    /** Resolve {@code raw} as the value for flag {@code name} using {@code resolver} and the live source. */
    static Object resolve(
            ParamResolver<?> resolver, CommandContext<CommandSourceStack> outer, String name, String raw) {
        try {
            return TokenResolution.resolve(resolver, outer.getSource(), name, raw);
        } catch (IllegalArgumentException badValue) {
            throw new IllegalArgumentException("Invalid value for --" + name + ": " + raw, badValue);
        }
    }
}

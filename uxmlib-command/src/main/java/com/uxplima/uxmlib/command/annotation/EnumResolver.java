package com.uxplima.uxmlib.command.annotation;

import java.util.Locale;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

/**
 * Resolves an enum parameter from a lower-cased constant name. Pure Java enums have no native client
 * argument type, so it parses a word and matches it case-insensitively against the constants, failing
 * with a clear message naming the allowed values. Tab-completion of the constant names is wired
 * separately from {@link #constantNames()}.
 */
final class EnumResolver implements ParamResolver<Enum<?>> {

    private final Enum<?>[] constants;

    EnumResolver(Enum<?>[] constants) {
        this.constants = constants.clone();
    }

    @Override
    public ArgumentType<?> argumentType(Arg arg) {
        return StringArgumentType.word();
    }

    @Override
    public Enum<?> resolve(CommandContext<CommandSourceStack> context, String name) {
        String raw = StringArgumentType.getString(context, name);
        for (Enum<?> constant : constants) {
            if (constant.name().equalsIgnoreCase(raw)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("expected one of " + java.util.Arrays.toString(constants) + ", got " + raw);
    }

    /** The constant names, lower-cased, for tab-completion. */
    String[] constantNames() {
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].name().toLowerCase(Locale.ROOT);
        }
        return names;
    }
}

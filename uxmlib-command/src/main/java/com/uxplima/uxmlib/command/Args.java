package com.uxplima.uxmlib.command;

import java.util.Objects;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Reads parsed arguments out of a {@link CommandContext} by name, one call per primitive type, so a
 * handler does not repeat the {@code XArgumentType.getX(ctx, name)} ceremony. The argument must have
 * been declared with the matching {@code ArgumentType}.
 */
public final class Args {

    private Args() {}

    /** The string argument named {@code name}. */
    public static String string(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(check(ctx, name), name);
    }

    /** The integer argument named {@code name}. */
    public static int integer(CommandContext<CommandSourceStack> ctx, String name) {
        return IntegerArgumentType.getInteger(check(ctx, name), name);
    }

    /** The double argument named {@code name}. */
    public static double number(CommandContext<CommandSourceStack> ctx, String name) {
        return DoubleArgumentType.getDouble(check(ctx, name), name);
    }

    /** The boolean argument named {@code name}. */
    public static boolean bool(CommandContext<CommandSourceStack> ctx, String name) {
        return BoolArgumentType.getBool(check(ctx, name), name);
    }

    private static CommandContext<CommandSourceStack> check(CommandContext<CommandSourceStack> ctx, String name) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(name, "name");
        return ctx;
    }
}

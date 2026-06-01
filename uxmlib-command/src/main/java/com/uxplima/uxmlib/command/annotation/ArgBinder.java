package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import org.jspecify.annotations.Nullable;

/**
 * Turns a parsed {@link CommandContext} into the actual argument array for a reflective handler call:
 * injects {@link Sender}/{@link CommandSourceStack}/{@link CommandSender}/{@link Player}, resolves each
 * {@code @}{@link Arg} through its {@link ParamResolver}, and fills an omitted optional argument with its
 * default. Split out of {@code AnnotatedCommands} so the registrar stays focused on tree building.
 */
final class ArgBinder {

    private ArgBinder() {}

    /** One {@code @Arg} parameter resolved to its resolver and source parameter. */
    record ParamArg(String name, Arg arg, ParamResolver<?> resolver, Parameter parameter) {}

    /** Build the argument array for {@code method} from {@code ctx}. */
    static Object[] bind(CommandContext<CommandSourceStack> ctx, Method method, List<ParamArg> args) {
        Parameter[] params = method.getParameters();
        Object[] callArgs = new Object[params.length];
        int argIndex = 0;
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (type == Player.class && !params[i].isAnnotationPresent(Arg.class)) {
                callArgs[i] = requirePlayer(ctx);
            } else if (type == Sender.class) {
                callArgs[i] = Sender.of(ctx.getSource());
            } else if (type == CommandSourceStack.class) {
                callArgs[i] = ctx.getSource();
            } else if (type == CommandSender.class) {
                callArgs[i] = ctx.getSource().getSender();
            } else {
                ParamArg pa = args.get(argIndex++);
                callArgs[i] = resolveOrDefault(ctx, pa, type);
            }
        }
        return callArgs;
    }

    private static @Nullable Object resolveOrDefault(
            CommandContext<CommandSourceStack> ctx, ParamArg pa, Class<?> type) {
        if (pa.arg().optional() && !hasArgument(ctx, pa.name())) {
            String def = pa.arg().def();
            return def.isEmpty() ? zeroValue(type) : Defaults.parse(type, def);
        }
        return pa.resolver().resolve(ctx, pa.name());
    }

    /** The sender as a Player, or a rejected-input error (caught and shown to the sender) if from console. */
    private static Player requirePlayer(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getSender() instanceof Player player) {
            return player;
        }
        throw new IllegalArgumentException("Only a player can run this command.");
    }

    /** Whether {@code name} was actually parsed in this dispatch (vs an omitted optional). */
    private static boolean hasArgument(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Object zeroValue(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return 0;
        }
        if (type == long.class || type == Long.class) {
            return 0L;
        }
        if (type == double.class || type == Double.class) {
            return 0.0d;
        }
        if (type == float.class || type == Float.class) {
            return 0.0f;
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        return null;
    }
}

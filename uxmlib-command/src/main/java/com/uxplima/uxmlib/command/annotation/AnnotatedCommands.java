package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.CommandRegistrar;
import com.uxplima.uxmlib.command.Sender;

/**
 * Turns an {@code @}{@link Command} handler into a registered Brigadier command by reflection: each
 * {@code @}{@link Subcommand} method becomes a branch under the root literal, its {@code @}{@link Arg}
 * parameters become typed arguments, a leading {@link Sender} or {@link CommandSourceStack} parameter is
 * injected, and {@code @}{@link Permission} becomes a {@code requires} gate. Malformed handlers fail at
 * registration with a {@link CommandParseException}, not at command-run time.
 *
 * <pre>{@code
 * @Command(name = "money")
 * class MoneyCommand {
 *     @Subcommand("pay") @Permission("money.pay")
 *     void pay(Sender sender, @Arg("target") String target, @Arg(value = "amount", min = 1) int amount) { ... }
 * }
 * AnnotatedCommands.register(plugin, new MoneyCommand());
 * }</pre>
 */
public final class AnnotatedCommands {

    private AnnotatedCommands() {}

    /** Reflect over {@code handler}, build its command tree, and register it with the server. */
    public static void register(JavaPlugin plugin, Object handler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(handler, "handler");
        Command command = handler.getClass().getAnnotation(Command.class);
        if (command == null) {
            throw new CommandParseException(handler.getClass().getName() + " is not annotated with @Command");
        }
        CommandRegistrar.register(plugin, buildNode(handler), command.description(), List.of(command.aliases()));
    }

    /**
     * Build the Brigadier tree for an annotated {@code handler} without registering it. Exposed so the
     * tree shape can be inspected and tested without a live plugin; {@link #register} delegates here.
     */
    public static LiteralCommandNode<CommandSourceStack> buildNode(Object handler) {
        Objects.requireNonNull(handler, "handler");
        Class<?> type = handler.getClass();
        Command command = type.getAnnotation(Command.class);
        if (command == null) {
            throw new CommandParseException(type.getName() + " is not annotated with @Command");
        }
        LiteralArgumentBuilder<CommandSourceStack> root = Cmd.literal(command.name());
        Permission classPermission = type.getAnnotation(Permission.class);
        if (classPermission != null) {
            root.requires(Cmd.permission(classPermission.value()));
        }

        List<Method> branches = orderedSubcommands(type);
        if (branches.isEmpty()) {
            throw new CommandParseException(type.getName() + " has no @Subcommand methods");
        }
        for (Method method : branches) {
            attachBranch(root, handler, method);
        }
        return root.build();
    }

    private static List<Method> orderedSubcommands(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subcommand.class)) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        // Longer literal paths first so "admin reload" is attached before a bare "" root executor; this
        // keeps node attachment order deterministic regardless of reflection's method ordering.
        methods.sort((a, b) -> Integer.compare(
                b.getAnnotation(Subcommand.class).value().length(),
                a.getAnnotation(Subcommand.class).value().length()));
        return methods;
    }

    private static void attachBranch(LiteralArgumentBuilder<CommandSourceStack> root, Object handler, Method method) {
        validateSignature(method);
        String path = method.getAnnotation(Subcommand.class).value().trim();
        com.mojang.brigadier.Command<CommandSourceStack> executor = executorFor(handler, method);
        ArgChain chain = buildArgChain(method, executor);

        String[] literals = path.isEmpty() ? new String[0] : path.split("\\s+");
        if (literals.length == 0) {
            // Root executor: a method-level @Permission on the root branch gates the whole root command.
            applyPermission(root, method);
            if (chain.firstArg != null) {
                root.then(chain.firstArg);
            } else {
                root.executes(executor);
            }
            return;
        }
        // Build the literal spine from the innermost outward so each then() nests correctly. The permission
        // gate goes on the first (outermost) literal of this branch.
        ArgumentBuilder<CommandSourceStack, ?> tail = Cmd.literal(literals[literals.length - 1]);
        if (chain.firstArg != null) {
            tail.then(chain.firstArg);
        } else {
            tail.executes(executor);
        }
        for (int i = literals.length - 2; i >= 0; i--) {
            LiteralArgumentBuilder<CommandSourceStack> parent = Cmd.literal(literals[i]);
            parent.then(tail);
            tail = parent;
        }
        applyPermission(tail, method);
        root.then(tail);
    }

    private static void applyPermission(ArgumentBuilder<CommandSourceStack, ?> builder, Method method) {
        Permission permission = method.getAnnotation(Permission.class);
        if (permission != null) {
            builder.requires(Cmd.permission(permission.value()));
        }
    }

    private static ArgChain buildArgChain(Method method, com.mojang.brigadier.Command<CommandSourceStack> executor) {
        List<ParamArg> args = argParameters(method);
        if (args.isEmpty()) {
            return new ArgChain(null);
        }
        // Nest arguments innermost-first; the deepest carries the executor.
        RequiredArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            ParamArg pa = args.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> builder =
                    Cmd.argument(pa.name, ArgType.argumentType(pa.type, pa.arg));
            if (tail == null) {
                builder.executes(executor);
            } else {
                builder.then(tail);
            }
            tail = builder;
        }
        return new ArgChain(tail);
    }

    private static com.mojang.brigadier.Command<CommandSourceStack> executorFor(Object handler, Method method) {
        List<ParamArg> args = argParameters(method);
        return ctx -> {
            Object[] callArgs = buildCallArgs(ctx, method, args);
            try {
                method.invoke(handler, callArgs);
                return Cmd.OK;
            } catch (java.lang.reflect.InvocationTargetException thrownByHandler) {
                // The handler itself failed. Don't let Brigadier dump a red stacktrace in the player's
                // chat: log the real cause server-side and reply with a clean, generic message.
                Throwable cause = thrownByHandler.getCause();
                ctx.getSource()
                        .getSender()
                        .getServer()
                        .getLogger()
                        .log(
                                java.util.logging.Level.SEVERE,
                                "Command '" + method.getName() + "' threw an exception",
                                cause != null ? cause : thrownByHandler);
                Sender.of(ctx.getSource())
                        .send(net.kyori.adventure.text.Component.text(
                                "An internal error occurred while running this command.",
                                net.kyori.adventure.text.format.NamedTextColor.RED));
                return 0;
            } catch (IllegalAccessException unreachable) {
                // setAccessible(true) ran at registration, so this cannot happen for a registered handler.
                throw new CommandParseException("could not invoke " + method.getName(), unreachable);
            }
        };
    }

    private static void validateSignature(Method method) {
        for (Parameter param : method.getParameters()) {
            Class<?> type = param.getType();
            boolean injectable =
                    type == Sender.class || type == CommandSourceStack.class || type == CommandSender.class;
            if (!injectable && !param.isAnnotationPresent(Arg.class)) {
                throw new CommandParseException("parameter '" + param.getName() + "' of " + method.getName()
                        + " must be @Arg-annotated or be a Sender/CommandSourceStack/CommandSender");
            }
        }
    }

    private static Object[] buildCallArgs(CommandContext<CommandSourceStack> ctx, Method method, List<ParamArg> args) {
        Parameter[] params = method.getParameters();
        Object[] callArgs = new Object[params.length];
        int argIndex = 0;
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (type == Sender.class) {
                callArgs[i] = Sender.of(ctx.getSource());
            } else if (type == CommandSourceStack.class) {
                callArgs[i] = ctx.getSource();
            } else if (type == CommandSender.class) {
                callArgs[i] = ctx.getSource().getSender();
            } else {
                ParamArg pa = args.get(argIndex++);
                callArgs[i] = ArgType.read(ctx, pa.name, pa.type);
            }
        }
        return callArgs;
    }

    private static List<ParamArg> argParameters(Method method) {
        List<ParamArg> args = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            Arg arg = param.getAnnotation(Arg.class);
            if (arg == null) {
                continue;
            }
            Class<?> type = param.getType();
            if (!ArgType.isSupported(type)) {
                throw new CommandParseException("unsupported @Arg type " + type.getName() + " on " + method.getName());
            }
            args.add(new ParamArg(arg.value(), type, arg));
        }
        return args;
    }

    private record ParamArg(String name, Class<?> type, Arg arg) {}

    /** The outermost argument builder of a branch (or {@code null} when the branch takes no arguments). */
    private record ArgChain(
            @org.jspecify.annotations.Nullable RequiredArgumentBuilder<CommandSourceStack, ?> firstArg) {}
}

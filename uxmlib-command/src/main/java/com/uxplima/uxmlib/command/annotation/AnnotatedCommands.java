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
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.CommandRegistrar;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/**
 * Turns an {@code @}{@link Command} handler into a registered Brigadier command by reflection: each
 * {@code @}{@link Subcommand} method becomes a branch under the root literal, its {@code @}{@link Arg}
 * parameters become typed arguments, a leading {@link Sender} or {@link CommandSourceStack} parameter is
 * injected, and {@code @}{@link Permission} becomes a {@code requires} gate. Malformed handlers fail at
 * registration with a {@link CommandParseException}, not at command-run time.
 * <pre>{@code
 * @Command(name = "money")
 * class MoneyCommand {
 *     @Subcommand("pay") @Permission("money.pay")
 *     void pay(Sender s, @Arg("target") Player t, @Arg(value = "amount", min = 1) int n) { ... }
 * }
 * AnnotatedCommands.register(plugin, new MoneyCommand());
 * }</pre>
 */
public final class AnnotatedCommands {

    private AnnotatedCommands() {}

    /** Reflect over {@code handler} with the default resolvers, build its tree, and register it. */
    public static void register(JavaPlugin plugin, Object handler) {
        register(plugin, handler, ParamResolvers.withDefaults());
    }

    /** Reflect over {@code handler} with {@code resolvers}, build its tree, and register it. */
    public static void register(JavaPlugin plugin, Object handler, ParamResolvers resolvers) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(handler, "handler");
        Command command = handler.getClass().getAnnotation(Command.class);
        if (command == null) {
            throw new CommandParseException(handler.getClass().getName() + " is not annotated with @Command");
        }
        CommandRegistrar.register(
                plugin, buildNode(handler, resolvers), command.description(), List.of(command.aliases()));
    }

    /** Build a handler's Brigadier tree with the default resolvers, without registering it. */
    public static LiteralCommandNode<CommandSourceStack> buildNode(Object handler) {
        return buildNode(handler, ParamResolvers.withDefaults());
    }

    /**
     * Build the Brigadier tree for an annotated {@code handler} without registering it, using
     * {@code resolvers} for its argument types. Exposed so the tree shape can be inspected and tested.
     */
    public static LiteralCommandNode<CommandSourceStack> buildNode(Object handler, ParamResolvers resolvers) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(resolvers, "resolvers");
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
            attachBranch(root, handler, method, resolvers);
        }
        if (command.help()) {
            root.then(HelpRenderer.helpLiteral(command.name(), branches));
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

    private static void attachBranch(
            LiteralArgumentBuilder<CommandSourceStack> root, Object handler, Method method, ParamResolvers resolvers) {
        validateSignature(method, resolvers);
        String path = method.getAnnotation(Subcommand.class).value().trim();
        com.mojang.brigadier.Command<CommandSourceStack> executor = executorFor(handler, method, resolvers);
        ArgChain chain = buildArgChain(method, executor, resolvers);

        String[] literals = path.isEmpty() ? new String[0] : path.split("\\s+");
        if (literals.length == 0) {
            // Root executor: a method-level @Permission on the root branch gates the whole root command.
            applyPermission(root, method);
            if (chain.firstArg != null) {
                root.then(chain.firstArg);
                if (chain.firstOptional) {
                    root.executes(executor); // the first argument is optional, so the bare root runs too
                }
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
            if (chain.firstOptional) {
                tail.executes(executor); // the first argument is optional, so the bare literal runs too
            }
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

    private static ArgChain buildArgChain(
            Method method, com.mojang.brigadier.Command<CommandSourceStack> executor, ParamResolvers resolvers) {
        List<ArgBinder.ParamArg> args = argParameters(method, resolvers);
        if (args.isEmpty()) {
            return new ArgChain(null, false);
        }
        checkArgOrder(method, args);
        // Nest arguments innermost-first; the deepest carries the executor. An optional argument ALSO
        // carries the executor itself, so Brigadier dispatches the shorter path when it is omitted and the
        // earlier (mandatory) argument's node already ends the command.
        RequiredArgumentBuilder<CommandSourceStack, ?> tail = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            ArgBinder.ParamArg pa = args.get(i);
            RequiredArgumentBuilder<CommandSourceStack, ?> builder =
                    Cmd.argument(pa.name(), pa.resolver().argumentType(pa.arg()));
            Suggestions.apply(builder, pa.parameter(), pa.resolver());
            if (tail == null) {
                builder.executes(executor);
            } else {
                builder.then(tail);
                if (args.get(i + 1).arg().optional()) {
                    builder.executes(executor); // the next arg is optional, so this node may end the command
                }
            }
            tail = builder;
        }
        return new ArgChain(tail, args.get(0).arg().optional());
    }

    private static void checkArgOrder(Method method, List<ArgBinder.ParamArg> args) {
        boolean seenOptional = false;
        for (int i = 0; i < args.size(); i++) {
            ArgBinder.ParamArg pa = args.get(i);
            if (seenOptional && !pa.arg().optional()) {
                throw new CommandParseException(
                        "a required argument cannot follow an optional one on " + method.getName());
            }
            seenOptional = seenOptional || pa.arg().optional();
            if (pa.arg().greedy() && i != args.size() - 1) {
                throw new CommandParseException("only the last argument may be greedy on " + method.getName());
            }
        }
    }

    private static com.mojang.brigadier.Command<CommandSourceStack> executorFor(
            Object handler, Method method, ParamResolvers resolvers) {
        List<ArgBinder.ParamArg> args = argParameters(method, resolvers);
        boolean playerOnly = method.isAnnotationPresent(PlayerOnly.class)
                || method.getDeclaringClass().isAnnotationPresent(PlayerOnly.class);
        return ctx -> {
            if (playerOnly && !(ctx.getSource().getSender() instanceof org.bukkit.entity.Player)) {
                Sender.of(ctx.getSource())
                        .send(net.kyori.adventure.text.Component.text(
                                "Only a player can run this command.",
                                net.kyori.adventure.text.format.NamedTextColor.RED));
                return 0;
            }
            Object[] callArgs;
            try {
                callArgs = ArgBinder.bind(ctx, method, args);
            } catch (IllegalArgumentException badArgument) {
                // A resolver rejected the input (e.g. an offline player, an out-of-range value). Reply with
                // its message rather than letting it surface as a server error.
                Sender.of(ctx.getSource())
                        .send(net.kyori.adventure.text.Component.text(
                                badArgument.getMessage() == null ? "Invalid argument." : badArgument.getMessage(),
                                net.kyori.adventure.text.format.NamedTextColor.RED));
                return 0;
            }
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

    private static boolean isInjectable(Class<?> type) {
        return type == Sender.class
                || type == CommandSourceStack.class
                || type == CommandSender.class
                || type == org.bukkit.entity.Player.class;
    }

    private static void validateSignature(Method method, ParamResolvers resolvers) {
        for (Parameter param : method.getParameters()) {
            Class<?> type = param.getType();
            boolean injectable = isInjectable(type) && !param.isAnnotationPresent(Arg.class);
            if (!injectable && !param.isAnnotationPresent(Arg.class)) {
                throw new CommandParseException("parameter '" + param.getName() + "' of " + method.getName()
                        + " must be @Arg-annotated or be a Sender/CommandSourceStack/CommandSender");
            }
            if (!injectable && !resolvers.supports(type)) {
                throw new CommandParseException(
                        "no resolver for @Arg type " + type.getName() + " on " + method.getName());
            }
        }
    }

    private static List<ArgBinder.ParamArg> argParameters(Method method, ParamResolvers resolvers) {
        List<ArgBinder.ParamArg> args = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            Arg arg = param.getAnnotation(Arg.class);
            if (arg == null) {
                continue;
            }
            ParamResolver<?> resolver = resolvers.resolverFor(param.getType());
            if (resolver == null) {
                throw new CommandParseException(
                        "no resolver for @Arg type " + param.getType().getName() + " on " + method.getName());
            }
            args.add(new ArgBinder.ParamArg(arg.value(), arg, resolver, param));
        }
        return args;
    }

    /**
     * The outermost argument builder of a branch (or {@code null} when the branch takes no arguments) and
     * whether that first argument is optional (so the node above it must also end the command).
     */
    private record ArgChain(
            @org.jspecify.annotations.Nullable RequiredArgumentBuilder<CommandSourceStack, ?> firstArg,
            boolean firstOptional) {}
}

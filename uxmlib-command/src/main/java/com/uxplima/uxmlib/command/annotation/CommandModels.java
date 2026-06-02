package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Flag;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.command.annotation.annotations.Switch;

/**
 * The reflective scan: reads an {@code @}{@link Command} handler into a {@link CommandModel} — the
 * platform-neutral IR a renderer walks. Each {@code @}{@link Subcommand} method becomes a {@link BranchModel}
 * carrying its literal path, permission, ordered positional {@code @}{@link Arg}s and {@code @}{@link
 * Flag}/{@code @}{@link Switch} entries. Malformed handlers (an unsupported argument type, a required
 * argument after an optional one, a non-trailing greedy, a parameter that is neither injectable nor a flag
 * nor an {@code @Arg}) fail here with a {@link CommandParseException}, before anything touches Brigadier.
 * This is the only place reflection meets the model; the renderer never sees a {@link Method}.
 */
final class CommandModels {

    private CommandModels() {}

    /** Reflect {@code handler} into its command model using {@code resolvers} for argument and flag types. */
    static CommandModel reflect(Object handler, ParamResolvers resolvers) {
        Class<?> type = handler.getClass();
        Command command = type.getAnnotation(Command.class);
        if (command == null) {
            throw new CommandParseException(type.getName() + " is not annotated with @Command");
        }
        List<Method> methods = orderedSubcommands(type);
        if (methods.isEmpty()) {
            throw new CommandParseException(type.getName() + " has no @Subcommand methods");
        }
        List<BranchModel> branches = new ArrayList<>();
        for (Method method : methods) {
            branches.add(branchOf(method, resolvers));
        }
        return new CommandModel(handler, command, type.getAnnotation(Permission.class), branches);
    }

    private static BranchModel branchOf(Method method, ParamResolvers resolvers) {
        validateSignature(method, resolvers);
        List<ArgBinder.ParamArg> args = argParameters(method, resolvers);
        List<FlagModel> flags = flagParameters(method, resolvers);
        checkParamOrder(method, args, !flags.isEmpty());
        String path = method.getAnnotation(Subcommand.class).value().trim();
        return new BranchModel(method, path, method.getAnnotation(Permission.class), args, flags);
    }

    private static List<Method> orderedSubcommands(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subcommand.class)) {
                method.setAccessible(true);
                methods.add(method);
            }
        }
        // Longer literal paths first so "admin reload" is attached before a bare "" root executor; this keeps
        // node attachment order deterministic regardless of reflection's method ordering.
        methods.sort((a, b) -> Integer.compare(
                b.getAnnotation(Subcommand.class).value().length(),
                a.getAnnotation(Subcommand.class).value().length()));
        return methods;
    }

    private static void validateSignature(Method method, ParamResolvers resolvers) {
        for (Parameter param : method.getParameters()) {
            if (isFlagParam(param)) {
                validateFlagParam(method, param, resolvers);
                continue;
            }
            boolean injectable = isInjectable(resolvers, param);
            if (!injectable && !param.isAnnotationPresent(Arg.class)) {
                throw new CommandParseException("parameter '" + param.getName() + "' of " + method.getName()
                        + " must be @Arg-annotated, a @Flag/@Switch, or a Sender/CommandSourceStack/CommandSender");
            }
            if (!injectable && !resolvers.supports(param)) {
                throw new CommandParseException(
                        "no resolver for @Arg type " + param.getType().getName() + " on " + method.getName());
            }
        }
    }

    private static void validateFlagParam(Method method, Parameter param, ParamResolvers resolvers) {
        if (param.isAnnotationPresent(Switch.class)) {
            Class<?> t = param.getType();
            if (t != boolean.class && t != Boolean.class) {
                throw new CommandParseException(
                        "@Switch parameter '" + flagName(param) + "' of " + method.getName() + " must be a boolean");
            }
            return;
        }
        if (!resolvers.supports(param.getType())) {
            throw new CommandParseException(
                    "no resolver for @Flag type " + param.getType().getName() + " on " + method.getName());
        }
    }

    private static boolean isInjectable(ParamResolvers resolvers, Parameter param) {
        return !param.isAnnotationPresent(Arg.class) && resolvers.hasContext(param.getType());
    }

    private static boolean isFlagParam(Parameter param) {
        return param.isAnnotationPresent(Flag.class) || param.isAnnotationPresent(Switch.class);
    }

    private static List<ArgBinder.ParamArg> argParameters(Method method, ParamResolvers resolvers) {
        List<ArgBinder.ParamArg> args = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            Arg arg = param.getAnnotation(Arg.class);
            if (arg == null) {
                continue;
            }
            ParamResolver<?> resolver = resolvers.resolverFor(param.getType(), param.getParameterizedType());
            if (resolver == null) {
                throw new CommandParseException(
                        "no resolver for @Arg type " + param.getType().getName() + " on " + method.getName());
            }
            args.add(new ArgBinder.ParamArg(arg.value(), arg, resolver, param));
        }
        return args;
    }

    private static List<FlagModel> flagParameters(Method method, ParamResolvers resolvers) {
        List<FlagModel> flags = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            Switch sw = param.getAnnotation(Switch.class);
            if (sw != null) {
                flags.add(FlagModel.switchFlag(sw.value(), sw.shorthand(), param));
                continue;
            }
            Flag flag = param.getAnnotation(Flag.class);
            if (flag != null) {
                flags.add(FlagModel.valueFlag(
                        flag.value(), flag.shorthand(), resolverFor(method, resolvers, param), param));
            }
        }
        return flags;
    }

    private static ParamResolver<?> resolverFor(Method method, ParamResolvers resolvers, Parameter param) {
        ParamResolver<?> resolver = resolvers.resolverFor(param.getType());
        if (resolver == null) {
            throw new CommandParseException(
                    "no resolver for @Flag type " + param.getType().getName() + " on " + method.getName());
        }
        return resolver;
    }

    private static String flagName(Parameter param) {
        Switch sw = param.getAnnotation(Switch.class);
        if (sw != null) {
            return sw.value();
        }
        Flag flag = param.getAnnotation(Flag.class);
        return flag != null ? flag.value() : param.getName();
    }

    /**
     * Reject argument and flag orderings Brigadier could not represent: a required argument after an optional
     * one, a greedy positional that is not last, a greedy positional alongside flags (the flags node is also
     * greedy, so two greedy siblings would be ambiguous), and any positional {@code @Arg} declared after a
     * flag (flags are consumed by a single greedy trailing node, so they must come last).
     */
    private static void checkParamOrder(Method method, List<ArgBinder.ParamArg> args, boolean hasFlags) {
        boolean seenOptional = false;
        for (int i = 0; i < args.size(); i++) {
            ArgBinder.ParamArg pa = args.get(i);
            if (seenOptional && !pa.arg().optional()) {
                throw new CommandParseException(
                        "a required argument cannot follow an optional one on " + method.getName());
            }
            seenOptional = seenOptional || pa.arg().optional();
            boolean consumesRest =
                    pa.arg().greedy() || isCollection(pa.parameter().getType());
            if (consumesRest && i != args.size() - 1) {
                throw new CommandParseException("only the last argument may be greedy on " + method.getName());
            }
            if (consumesRest && hasFlags) {
                throw new CommandParseException(
                        "a greedy argument cannot be combined with @Flag/@Switch on " + method.getName());
            }
        }
        checkFlagsLast(method);
    }

    /** Whether a parameter type is one of the composing collection types, which consume a greedy trailing node. */
    private static boolean isCollection(Class<?> type) {
        return type == java.util.List.class || type == java.util.Optional.class;
    }

    private static void checkFlagsLast(Method method) {
        boolean seenFlag = false;
        for (Parameter param : method.getParameters()) {
            if (isFlagParam(param)) {
                seenFlag = true;
            } else if (seenFlag && param.isAnnotationPresent(Arg.class)) {
                throw new CommandParseException(
                        "a positional @Arg cannot follow a @Flag/@Switch on " + method.getName());
            }
        }
    }
}

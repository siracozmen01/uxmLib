package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import org.jspecify.annotations.Nullable;

/**
 * Turns a parsed {@link CommandContext} into the actual argument array for a reflective handler call: a
 * non-{@code @Arg} parameter is filled by its registered {@link ContextParameter} (the sender-shaped
 * injectables plus any a consumer added); each {@code @}{@link Arg} is parsed by its {@link ParamResolver},
 * re-checked by the parameter's {@code @Range}/{@code @Length} and any registered
 * {@link ParameterValidator}, and an omitted optional argument is filled with its default. Split out of
 * {@code AnnotatedCommands} so the registrar stays focused on tree building.
 */
final class ArgBinder {

    private ArgBinder() {}

    /** One {@code @Arg} parameter resolved to its resolver and source parameter. */
    record ParamArg(String name, Arg arg, ParamResolver<?> resolver, Parameter parameter) {}

    /** Build the argument array for {@code method} from {@code ctx}, including any parsed flags. */
    static Object[] bind(
            CommandContext<CommandSourceStack> ctx,
            Method method,
            List<ParamArg> args,
            List<FlagModel> flags,
            ParamResolvers resolvers) {
        Parameter[] params = method.getParameters();
        Object[] callArgs = new Object[params.length];
        Flags parsedFlags = flags.isEmpty() ? null : parsedFlags(ctx);
        Map<Parameter, FlagModel> flagByParam = flagByParam(flags);
        int argIndex = 0;
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            FlagModel flag = flagByParam.get(param);
            if (flag != null) {
                callArgs[i] = bindFlag(ctx, flag, parsedFlags);
            } else if (param.isAnnotationPresent(Arg.class)) {
                callArgs[i] = resolveArg(ctx, args.get(argIndex++), resolvers);
            } else {
                callArgs[i] = inject(ctx, param, resolvers);
            }
        }
        return callArgs;
    }

    private static Map<Parameter, FlagModel> flagByParam(List<FlagModel> flags) {
        Map<Parameter, FlagModel> map = new HashMap<>();
        for (FlagModel flag : flags) {
            map.put(flag.parameter(), flag);
        }
        return map;
    }

    /** The parsed flags for this dispatch, or an empty result when the optional flags node was not reached. */
    private static Flags parsedFlags(CommandContext<CommandSourceStack> ctx) {
        if (hasArgument(ctx, "flags")) {
            return ctx.getArgument("flags", Flags.class);
        }
        return new Flags(Map.of(), Map.of());
    }

    /**
     * Bind one flag/switch parameter from the parsed {@code flags}. A switch yields its presence boolean; a
     * value flag's raw token is handed to its resolver via a synthetic single-argument context so the same
     * resolver an {@code @Arg} uses parses it. An omitted value flag yields the parameter type's zero value.
     */
    private static @Nullable Object bindFlag(
            CommandContext<CommandSourceStack> ctx, FlagModel flag, @Nullable Flags flags) {
        Flags resolved = flags == null ? new Flags(Map.of(), Map.of()) : flags;
        if (!flag.isValueFlag()) {
            return resolved.isSet(flag.name());
        }
        String raw = resolved.value(flag.name());
        if (raw == null) {
            return zeroValue(flag.parameter().getType());
        }
        ParamResolver<?> resolver = flag.resolver();
        if (resolver == null) {
            throw new CommandParseException("value flag '" + flag.name() + "' has no resolver");
        }
        return FlagValues.resolve(resolver, ctx, flag.name(), raw);
    }

    private static Object inject(CommandContext<CommandSourceStack> ctx, Parameter param, ParamResolvers resolvers) {
        ContextParameter<?> provider = resolvers.contextFor(param.getType());
        if (provider == null) {
            // validateSignature already rejected such a handler at registration; defend the run path anyway.
            throw new CommandParseException(
                    "no context provider for parameter type " + param.getType().getName());
        }
        return provider.provide(ctx);
    }

    private static @Nullable Object resolveArg(
            CommandContext<CommandSourceStack> ctx, ParamArg pa, ParamResolvers resolvers) {
        try {
            Object value = resolveOrDefault(ctx, pa, pa.parameter().getType());
            ArgValidators.check(pa.parameter(), value);
            runValidators(resolvers, pa, value);
            return value;
        } catch (ArgumentResolveException alreadyTyped) {
            throw alreadyTyped;
        } catch (IllegalArgumentException rejected) {
            // Re-throw with which argument failed and the raw input the sender gave, so the reply can point at
            // the exact argument rather than a flat message. The original message becomes the typed reason.
            throw new ArgumentResolveException(
                    new ErrorContext(pa.name(), rawInput(ctx, pa.name()), reasonOf(rejected)), rejected);
        }
    }

    /** The raw text the sender gave for the node named {@code name}, or {@code ""} when it cannot be read. */
    private static String rawInput(CommandContext<CommandSourceStack> ctx, String name) {
        String input = ctx.getInput();
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) {
                int start = Math.min(node.getRange().getStart(), input.length());
                int end = Math.min(node.getRange().getEnd(), input.length());
                return start <= end ? input.substring(start, end) : "";
            }
        }
        return "";
    }

    private static String reasonOf(IllegalArgumentException rejected) {
        String message = rejected.getMessage();
        return message == null ? "" : message;
    }

    @SuppressWarnings("unchecked") // a validator registered for type T only sees a value resolved as T
    private static void runValidators(ParamResolvers resolvers, ParamArg pa, @Nullable Object value) {
        for (ParameterValidator<?> validator :
                resolvers.validatorsFor(pa.parameter().getType())) {
            ((ParameterValidator<Object>) validator).validate(value, pa.arg());
        }
    }

    private static @Nullable Object resolveOrDefault(
            CommandContext<CommandSourceStack> ctx, ParamArg pa, Class<?> type) {
        if (pa.arg().optional() && !hasArgument(ctx, pa.name())) {
            String def = pa.arg().def();
            return def.isEmpty() ? zeroValue(type) : Defaults.parse(type, def);
        }
        return pa.resolver().resolve(ctx, pa.name());
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
        if (type == java.util.Optional.class) {
            return java.util.Optional.empty();
        }
        if (type == java.util.List.class) {
            return java.util.List.of();
        }
        return null;
    }
}

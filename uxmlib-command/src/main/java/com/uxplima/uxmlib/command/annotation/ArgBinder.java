package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

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

    /** Build the argument array for {@code method} from {@code ctx}. */
    static Object[] bind(
            CommandContext<CommandSourceStack> ctx, Method method, List<ParamArg> args, ParamResolvers resolvers) {
        Parameter[] params = method.getParameters();
        Object[] callArgs = new Object[params.length];
        int argIndex = 0;
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(Arg.class)) {
                callArgs[i] = resolveArg(ctx, args.get(argIndex++), resolvers);
            } else {
                callArgs[i] = inject(ctx, params[i], resolvers);
            }
        }
        return callArgs;
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
        Object value = resolveOrDefault(ctx, pa, pa.parameter().getType());
        ArgValidators.check(pa.parameter(), value);
        runValidators(resolvers, pa, value);
        return value;
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
        return null;
    }
}

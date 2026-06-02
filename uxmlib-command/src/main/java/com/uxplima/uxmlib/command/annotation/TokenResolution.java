package com.uxplima.uxmlib.command.annotation;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * Parses a single raw token through a {@link ParamResolver} the same way a positional {@code @}{@link Arg}
 * would, by feeding it through a throwaway one-argument Brigadier tree built from the resolver's own argument
 * type and handing the resulting context back to the resolver. A {@link ParamResolver} only knows how to read
 * its value out of a {@link CommandContext} by name, but a flag token or a list element is not a node in the
 * dispatched tree; this bridges that gap so flag values and collection elements stay type-uniform with
 * positional arguments without the resolver knowing where the token came from. Shared by {@link FlagValues}
 * (one token) and {@link CollectionResolvers} (one per element).
 */
final class TokenResolution {

    private TokenResolution() {}

    /** Resolve {@code raw} as the value named {@code name} through {@code resolver} against {@code source}. */
    static Object resolve(ParamResolver<?> resolver, CommandSourceStack source, String name, String raw) {
        if (resolver.nativeArgument()) {
            // The standalone dispatcher built below has no Paper registry/build context, so a native (NMS-backed)
            // argument type cannot parse here. Registration already rejects native flags/collection elements; this
            // is the runtime backstop for a custom resolver that wraps a native type without flagging itself.
            throw new IllegalArgumentException(
                    "native argument type for " + name + " cannot be resolved as a flag value or collection element");
        }
        CommandContext<CommandSourceStack> parsed = parse(resolver, source, name, raw);
        Object value = resolver.resolve(parsed, name);
        if (value == null) {
            throw new IllegalArgumentException("invalid value for " + name + ": " + raw);
        }
        return value;
    }

    private static CommandContext<CommandSourceStack> parse(
            ParamResolver<?> resolver, CommandSourceStack source, String name, String raw) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Cmd.literal("token").then(Cmd.argument(name, argumentType(resolver))));
        ParseResults<CommandSourceStack> results = dispatcher.parse("token " + raw, source);
        if (!results.getExceptions().isEmpty() || results.getReader().canRead()) {
            throw new IllegalArgumentException("invalid value for " + name + ": " + raw);
        }
        return results.getContext().build("token " + raw);
    }

    /** The native argument type for one token: a plain (non-greedy) read, since a token is a single word. */
    private static ArgumentType<?> argumentType(ParamResolver<?> resolver) {
        return resolver.argumentType(DEFAULT_ARG);
    }

    private static final Arg DEFAULT_ARG = defaultArg();

    /**
     * A default {@code @Arg} (no bounds, not greedy, not optional) handed to {@link
     * ParamResolver#argumentType(Arg)} when building a token's throwaway parse tree. Read off the
     * {@code @Arg}-annotated parameter of {@link #defaultArgHolder(Object)} rather than hand-implemented, so
     * it is a real annotation proxy with the equals/hashCode the platform expects of an annotation.
     */
    private static Arg defaultArg() {
        try {
            Arg arg = TokenResolution.class
                    .getDeclaredMethod("defaultArgHolder", Object.class)
                    .getParameters()[0]
                    .getAnnotation(Arg.class);
            if (arg == null) {
                throw new IllegalStateException("defaultArgHolder lost its @Arg annotation");
            }
            return arg;
        } catch (NoSuchMethodException impossible) {
            throw new IllegalStateException("defaultArgHolder method is missing", impossible);
        }
    }

    @SuppressWarnings("unused") // only its parameter's @Arg annotation is read reflectively, never called
    private static void defaultArgHolder(@Arg("") Object placeholder) {}
}

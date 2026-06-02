package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import org.jspecify.annotations.Nullable;

/**
 * The composing resolvers: a {@code List<T>}, {@code Optional<T>}, or {@code T[]} parameter is resolved by
 * deriving a resolver from its element type's own resolver, registered as a {@link ParamResolver.Factory
 * decline-chain factory} so it composes over whatever element types the registry knows. A {@code List<T>} or
 * {@code T[]} reads one greedy trailing token blob and maps every whitespace-separated token through the
 * element resolver (a {@code T[]} collecting them into a typed array, primitives included — the natural shape
 * for trailing "orphan" free args); an {@code Optional<T>} reads one greedy token and is present only when a
 * token was actually given. All ride {@link TokenResolution} for the per-token parse, so a {@code List<World>}
 * resolves each world exactly like an {@code @Arg World} would. Only a trailing parameter can be one of these,
 * since each consumes a greedy node.
 */
final class CollectionResolvers {

    private CollectionResolvers() {}

    static void installInto(ParamResolvers r) {
        r.factory(CollectionResolvers::listFactory);
        r.factory(CollectionResolvers::optionalFactory);
        r.factory(CollectionResolvers::arrayFactory);
    }

    /** Split {@code raw} into tokens and resolve each through {@code element}; a blank blob yields no elements. */
    static List<Object> resolveTokens(ParamResolver<?> element, CommandSourceStack source, String raw) {
        List<Object> values = new ArrayList<>();
        for (String token : tokenize(raw)) {
            values.add(TokenResolution.resolve(element, source, "element", token));
        }
        return values;
    }

    /**
     * The whitespace-separated tokens of {@code raw}, with no empty tokens for runs of spaces or a leading or
     * trailing gap. Hand-rolled rather than {@code String.split} both because the regex form has surprising
     * trailing-empty behaviour and to keep an empty blob mapping cleanly to no tokens.
     */
    private static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                if (start >= 0) {
                    tokens.add(raw.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            tokens.add(raw.substring(start));
        }
        return tokens;
    }

    /** Resolve {@code raw} as an optional value: present when a token is given, empty when the blob is blank. */
    static Optional<Object> resolveOptional(ParamResolver<?> element, CommandSourceStack source, String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(TokenResolution.resolve(element, source, "element", trimmed));
    }

    private static @Nullable ParamResolver<?> listFactory(Class<?> rawType, Type genericType, ParamResolvers registry) {
        if (rawType != List.class) {
            return null;
        }
        ParamResolver<?> element = elementResolver(genericType, registry);
        return element == null ? null : new ListResolver(element);
    }

    private static @Nullable ParamResolver<?> optionalFactory(
            Class<?> rawType, Type genericType, ParamResolvers registry) {
        if (rawType != Optional.class) {
            return null;
        }
        ParamResolver<?> element = elementResolver(genericType, registry);
        return element == null ? null : new OptionalResolver(element);
    }

    private static @Nullable ParamResolver<?> arrayFactory(
            Class<?> rawType, Type genericType, ParamResolvers registry) {
        if (!rawType.isArray()) {
            return null;
        }
        ParamResolver<?> element = arrayElementResolver(rawType.getComponentType(), registry);
        return element == null ? null : new ArrayResolver(rawType.getComponentType(), element);
    }

    /** The resolver for an array's component type, rejecting a native element exactly as a {@code List<T>} does. */
    private static @Nullable ParamResolver<?> arrayElementResolver(Class<?> componentType, ParamResolvers registry) {
        ParamResolver<?> element = registry.resolverFor(componentType, componentType);
        if (element != null && element.nativeArgument()) {
            throw new CommandParseException("an array of native type " + componentType.getName()
                    + " is not supported; a native argument (player/world/location/sound/...) cannot be an"
                    + " array element. Take a single one as a positional @Arg instead.");
        }
        return element;
    }

    /** The resolver for the single type argument of a {@code List<T>}/{@code Optional<T>}, or null when absent. */
    private static @Nullable ParamResolver<?> elementResolver(Type genericType, ParamResolvers registry) {
        if (!(genericType instanceof ParameterizedType parameterized)) {
            return null;
        }
        Type[] args = parameterized.getActualTypeArguments();
        if (args.length != 1 || !(args[0] instanceof Class<?> elementClass)) {
            return null;
        }
        ParamResolver<?> element = registry.resolverFor(elementClass, args[0]);
        if (element != null && element.nativeArgument()) {
            // Each element is resolved by feeding one token through a standalone Brigadier dispatcher
            // (TokenResolution), which has no Paper registry/build context; a native element type
            // (player/world/location/sound/...) cannot parse there and would throw on a live server. Reject the
            // whole collection at registration with a clear message rather than fail per element at runtime.
            throw new CommandParseException("a List/Optional of native type " + elementClass.getName()
                    + " is not supported; a native argument (player/world/location/sound/...) cannot be a"
                    + " collection element. Take a single one as a positional @Arg instead.");
        }
        return element;
    }

    /** A resolver that greedily consumes the rest of the input and maps every token through the element. */
    private static final class ListResolver implements ParamResolver<List<Object>> {
        private final ParamResolver<?> element;

        ListResolver(ParamResolver<?> element) {
            this.element = element;
        }

        @Override
        public ArgumentType<?> argumentType(Arg arg) {
            return StringArgumentType.greedyString();
        }

        @Override
        public List<Object> resolve(CommandContext<CommandSourceStack> context, String name) {
            String raw = StringArgumentType.getString(context, name);
            return resolveTokens(element, context.getSource(), raw);
        }

        @Override
        public @Nullable Collection<String> suggestions() {
            return element.suggestions();
        }
    }

    /** A resolver that consumes one greedy token and is present only when a token was given. */
    private static final class OptionalResolver implements ParamResolver<Optional<Object>> {
        private final ParamResolver<?> element;

        OptionalResolver(ParamResolver<?> element) {
            this.element = element;
        }

        @Override
        public ArgumentType<?> argumentType(Arg arg) {
            return StringArgumentType.greedyString();
        }

        @Override
        public Optional<Object> resolve(CommandContext<CommandSourceStack> context, String name) {
            String raw = StringArgumentType.getString(context, name);
            return resolveOptional(element, context.getSource(), raw);
        }

        @Override
        public @Nullable Collection<String> suggestions() {
            return element.suggestions();
        }
    }

    /**
     * A resolver that greedily consumes the rest of the input and maps every token through the element into a
     * typed array of the parameter's component type. A reflective {@link java.lang.reflect.Array} is built so a
     * primitive component ({@code int[]}, {@code double[]}) gets its values unboxed in, matching the handler's
     * exact parameter type.
     */
    private static final class ArrayResolver implements ParamResolver<Object> {
        private final Class<?> componentType;
        private final ParamResolver<?> element;

        ArrayResolver(Class<?> componentType, ParamResolver<?> element) {
            this.componentType = componentType;
            this.element = element;
        }

        @Override
        public ArgumentType<?> argumentType(Arg arg) {
            return StringArgumentType.greedyString();
        }

        @Override
        public Object resolve(CommandContext<CommandSourceStack> context, String name) {
            String raw = StringArgumentType.getString(context, name);
            List<Object> values = resolveTokens(element, context.getSource(), raw);
            Object array = java.lang.reflect.Array.newInstance(componentType, values.size());
            for (int i = 0; i < values.size(); i++) {
                java.lang.reflect.Array.set(array, i, values.get(i));
            }
            return array;
        }

        @Override
        public @Nullable Collection<String> suggestions() {
            return element.suggestions();
        }
    }
}

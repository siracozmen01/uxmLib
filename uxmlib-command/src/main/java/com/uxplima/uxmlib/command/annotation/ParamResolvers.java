package com.uxplima.uxmlib.command.annotation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A registry of {@link ParamResolver}s, keyed by the Java parameter type they handle. An instance is
 * passed to {@link AnnotatedCommands} so a plugin can teach the command DSL new argument types without any
 * global mutable state. {@link #withDefaults()} returns a registry pre-loaded with resolvers for the
 * primitives and common Bukkit types (player, world, material, enums, UUID); add your own with
 * {@link #register}.
 */
public final class ParamResolvers {

    private final Map<Class<?>, ParamResolver<?>> byType = new HashMap<>();

    private ParamResolvers() {}

    /** An empty registry. Usually you want {@link #withDefaults()} instead. */
    public static ParamResolvers empty() {
        return new ParamResolvers();
    }

    /** A registry pre-loaded with the built-in resolvers (primitives, player, world, material, enum, uuid). */
    public static ParamResolvers withDefaults() {
        ParamResolvers resolvers = new ParamResolvers();
        BuiltinResolvers.installInto(resolvers);
        return resolvers;
    }

    /** Register {@code resolver} for parameters of {@code type}. Returns this for chaining. */
    public <T> ParamResolvers register(Class<T> type, ParamResolver<T> resolver) {
        byType.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(resolver, "resolver"));
        return this;
    }

    /** Whether some resolver handles {@code type} (directly, or as an enum). */
    boolean supports(Class<?> type) {
        return resolverFor(type) != null;
    }

    /** The resolver for {@code type}, or {@code null} if none is registered. Enums share one resolver. */
    @Nullable ParamResolver<?> resolverFor(Class<?> type) {
        ParamResolver<?> direct = byType.get(type);
        if (direct != null) {
            return direct;
        }
        if (type.isEnum()) {
            return BuiltinResolvers.enumResolver(type);
        }
        return null;
    }
}

package com.uxplima.uxmlib.command.annotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final Map<Class<?>, List<ParameterValidator<?>>> validatorsByType = new HashMap<>();
    private final Map<Class<?>, ContextParameter<?>> contextByType = new HashMap<>();
    private final List<CommandCondition> conditions = new ArrayList<>();
    private Cooldowns cooldowns = new Cooldowns();

    private ParamResolvers() {}

    /** An empty registry. Usually you want {@link #withDefaults()} instead. */
    public static ParamResolvers empty() {
        return new ParamResolvers();
    }

    /** A registry pre-loaded with the built-in resolvers (primitives, player, world, material, enum, uuid). */
    public static ParamResolvers withDefaults() {
        ParamResolvers resolvers = new ParamResolvers();
        BuiltinResolvers.installInto(resolvers);
        ContextParameters.installInto(resolvers);
        return resolvers;
    }

    /** Register {@code resolver} for parameters of {@code type}. Returns this for chaining. */
    public <T> ParamResolvers register(Class<T> type, ParamResolver<T> resolver) {
        byType.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(resolver, "resolver"));
        return this;
    }

    /**
     * Register a post-resolve {@code validator} for parameters of {@code type}. Several validators may share
     * a type; each runs against the resolved value and rejects bad input by throwing
     * {@link IllegalArgumentException}. Returns this for chaining.
     */
    public <T> ParamResolvers validate(Class<T> type, ParameterValidator<T> validator) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(validator, "validator");
        validatorsByType.computeIfAbsent(type, ignored -> new ArrayList<>()).add(validator);
        return this;
    }

    /**
     * Register a {@code provider} that injects a non-{@code @Arg} parameter of {@code type} from the command
     * context. Replaces any provider already registered for that exact type. Returns this for chaining.
     */
    public <T> ParamResolvers context(Class<T> type, ContextParameter<T> provider) {
        contextByType.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(provider, "provider"));
        return this;
    }

    /** Register a pre-execute {@code condition} run before every branch's handler. Returns this for chaining. */
    public ParamResolvers condition(CommandCondition condition) {
        conditions.add(Objects.requireNonNull(condition, "condition"));
        return this;
    }

    /**
     * Use {@code cooldowns} as the store backing {@code @}{@link
     * com.uxplima.uxmlib.command.annotation.annotations.Cooldown} for branches built with this registry.
     * Pass a shared instance to make several registrations share one set of windows, or one with a
     * controllable clock in tests. Defaults to a fresh wall-clock store. Returns this for chaining.
     */
    public ParamResolvers cooldowns(Cooldowns cooldowns) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        return this;
    }

    /** The cooldown store backing {@code @Cooldown} for branches built with this registry. */
    Cooldowns cooldowns() {
        return cooldowns;
    }

    /** Whether some resolver handles {@code type} (directly, or as an enum). */
    boolean supports(Class<?> type) {
        return resolverFor(type) != null;
    }

    /** The validators registered for {@code type}, in registration order; empty when none. */
    List<ParameterValidator<?>> validatorsFor(Class<?> type) {
        List<ParameterValidator<?>> found = validatorsByType.get(type);
        return found == null ? List.of() : found;
    }

    /** Whether a context provider is registered for the exact {@code type}. */
    boolean hasContext(Class<?> type) {
        return contextByType.containsKey(type);
    }

    /** The context provider for the exact {@code type}, or {@code null} if none is registered. */
    @Nullable ContextParameter<?> contextFor(Class<?> type) {
        return contextByType.get(type);
    }

    /** The pre-execute conditions, in registration order. */
    List<CommandCondition> conditions() {
        return conditions;
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

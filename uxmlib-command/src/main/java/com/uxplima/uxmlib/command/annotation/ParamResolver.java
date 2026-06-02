package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Parameter;
import java.util.Collection;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import org.jspecify.annotations.Nullable;

/**
 * Turns one Java parameter type into a Brigadier argument: the {@link ArgumentType} to register for it,
 * and how to read the parsed value back out of the {@link CommandContext} as a {@code T}. Built-in
 * resolvers map rich types — {@link org.bukkit.entity.Player}, {@link org.bukkit.World}, enums, and the
 * primitives — onto native Paper argument types, so the client gets validation and tab-completion for
 * free. Register your own on a {@link ParamResolvers} registry to add domain types.
 *
 * @param <T> the resolved value type handed to the command method
 */
public interface ParamResolver<T> {

    /** The native Brigadier argument type to register for this parameter. */
    ArgumentType<?> argumentType(Arg arg);

    /**
     * The native Brigadier argument type for {@code parameter}, given a chance to read parameter-level
     * annotations the {@link Arg} alone does not carry (e.g. {@code @}{@link
     * com.uxplima.uxmlib.command.annotation.annotations.Range}). Defaults to {@link #argumentType(Arg)};
     * the numeric built-ins override it to fold {@code @Range} bounds into the native type. Consumers only
     * implement {@link #argumentType(Arg)}.
     */
    default ArgumentType<?> argumentType(Arg arg, Parameter parameter) {
        return argumentType(arg);
    }

    /** Read the parsed value for {@code name} out of {@code context}. */
    T resolve(CommandContext<CommandSourceStack> context, String name);

    /**
     * The completions to offer for this parameter, or {@code null} to leave the argument type's native
     * suggestions (a player/world/material arg completes itself). A resolver over a plain word — an enum,
     * a custom type — overrides this to drive tab-completion.
     */
    default @Nullable Collection<String> suggestions() {
        return null;
    }

    /**
     * A resolver source that can <em>decline</em>: given a parameter's full generic type, it either produces
     * a {@link ParamResolver} for it or returns {@code null} to let the next factory try. This is the seam
     * the composing resolvers ride — a {@code List<T>} or {@code Optional<T>} factory derives a resolver from
     * the element type's own resolver, declining for any raw type it does not recognise. Factories are
     * consulted in registration order, after the direct per-type registrations, so a direct
     * {@link ParamResolvers#register(Class, ParamResolver)} always wins over a factory for the same raw type.
     */
    @FunctionalInterface
    interface Factory {

        /**
         * Build a resolver for the parameter whose erased type is {@code rawType} and whose full generic type
         * is {@code genericType} (e.g. {@code List<World>}), looking up element resolvers through
         * {@code registry}, or return {@code null} to decline and let the next factory try.
         */
        @Nullable ParamResolver<?> create(Class<?> rawType, java.lang.reflect.Type genericType, ParamResolvers registry);
    }
}

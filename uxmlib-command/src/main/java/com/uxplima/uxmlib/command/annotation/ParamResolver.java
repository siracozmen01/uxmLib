package com.uxplima.uxmlib.command.annotation;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;

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

    /** Read the parsed value for {@code name} out of {@code context}. */
    T resolve(CommandContext<CommandSourceStack> context, String name);
}

package com.uxplima.uxmlib.command.annotation;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;

/**
 * Supplies a non-argument handler parameter from the command context rather than from parsed input. Where a
 * {@link ParamResolver} reads a value the sender typed, a context parameter derives a value from <em>who</em>
 * and <em>where</em> the command ran — the {@link com.uxplima.uxmlib.command.Sender}, the raw
 * {@link CommandSourceStack}, the {@link org.bukkit.command.CommandSender}, the executing
 * {@link org.bukkit.entity.Player}, and anything a consumer wants to inject (the sender's world, an economy
 * handle, a per-command cooldown view). A parameter is treated as a context parameter when it carries no
 * {@code @}{@link com.uxplima.uxmlib.command.annotation.annotations.Arg} and its type has a registered
 * provider. Register one on a {@link ParamResolvers} registry with
 * {@link ParamResolvers#context(Class, ContextParameter)}.
 *
 * @param <T> the injected parameter type
 */
@FunctionalInterface
public interface ContextParameter<T> {

    /**
     * Produce the value to inject for this parameter from {@code context}. A provider that cannot supply a
     * value for the current sender (e.g. a {@link org.bukkit.entity.Player} parameter for a console sender)
     * rejects by throwing an {@link IllegalArgumentException}; its message is shown to the sender on the
     * same clean-error path a rejected argument uses.
     */
    T provide(CommandContext<CommandSourceStack> context);
}

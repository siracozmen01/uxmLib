package com.uxplima.uxmlib.command;

import java.util.Objects;
import java.util.function.Predicate;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

/**
 * Node-builder factories bound to {@link CommandSourceStack}. Paper's {@code Commands.literal} /
 * {@code Commands.argument} already return the right builders, but callers otherwise have to spell out
 * {@code <CommandSourceStack>} repeatedly and fight generic inference; these delegate so the source type
 * is implicit. {@link #OK} is Brigadier's success code, returned from an {@code executes} handler.
 */
public final class Cmd {

    /** The integer an {@code executes} handler returns on success. */
    public static final int OK = Command.SINGLE_SUCCESS;

    private Cmd() {}

    /** A literal (keyword) command node, e.g. {@code Cmd.literal("home")}. */
    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        Objects.requireNonNull(name, "name");
        return Commands.literal(name);
    }

    /** A typed argument node, e.g. {@code Cmd.argument("amount", IntegerArgumentType.integer(1))}. */
    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        return Commands.argument(name, type);
    }

    /** A {@code requires} predicate that passes when the sender holds {@code permission}. */
    public static Predicate<CommandSourceStack> permission(String permission) {
        Objects.requireNonNull(permission, "permission");
        return source -> source.getSender().hasPermission(permission);
    }
}

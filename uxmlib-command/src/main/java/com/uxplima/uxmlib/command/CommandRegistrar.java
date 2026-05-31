package com.uxplima.uxmlib.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

/**
 * Registers Brigadier commands through Paper's {@code LifecycleEvents.COMMANDS} event, hiding the
 * boilerplate that is easy to get wrong (the right event package, the {@code run} handler method, the
 * {@code ReloadableRegistrarEvent<Commands>} generic). Call from {@code onEnable}:
 *
 * <pre>{@code
 * CommandRegistrar.register(this,
 *     Cmd.literal("ping").executes(ctx -> { Sender.of(ctx.getSource()).send(Component.text("pong")); return Cmd.OK; }),
 *     "Replies with pong");
 * }</pre>
 */
public final class CommandRegistrar {

    private CommandRegistrar() {}

    /** Register a built command node with a description and aliases. */
    public static void register(
            JavaPlugin plugin,
            LiteralCommandNode<CommandSourceStack> node,
            String description,
            List<String> aliases) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(aliases, "aliases");
        List<String> aliasCopy = List.copyOf(aliases);
        plugin.getLifecycleManager()
                .registerEventHandler(LifecycleEvents.COMMANDS, event -> {
                    Commands commands = event.registrar();
                    commands.register(node, description, aliasCopy);
                });
    }

    /** Register a command builder (built for you) with a description and varargs aliases. */
    public static void register(
            JavaPlugin plugin,
            LiteralArgumentBuilder<CommandSourceStack> builder,
            String description,
            String... aliases) {
        Objects.requireNonNull(builder, "builder");
        register(plugin, builder.build(), description, List.of(aliases));
    }
}

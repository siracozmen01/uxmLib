package com.uxplima.uxmlib.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;

/**
 * A small read view over a {@link CommandSourceStack}: who ran the command and how to reply to them.
 * Wrap the source from an {@code executes} handler with {@link #of} to get {@link #player()} as an
 * {@link Optional} and a direct {@link #send(Component)} without repeating the unwrap each time.
 */
public final class Sender {

    private final CommandSourceStack source;

    private Sender(CommandSourceStack source) {
        this.source = source;
    }

    /** Wrap a command source. */
    public static Sender of(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        return new Sender(source);
    }

    /** The underlying Bukkit sender (a player, the console, or a command block). */
    public CommandSender bukkit() {
        return source.getSender();
    }

    /** The sender as a {@link Player}, or empty when the command came from console or a block. */
    public Optional<Player> player() {
        return source.getSender() instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    /** Whether a player ran the command. */
    public boolean isPlayer() {
        return source.getSender() instanceof Player;
    }

    /** Send a message to the sender. */
    public void send(Component message) {
        Objects.requireNonNull(message, "message");
        source.getSender().sendMessage(message);
    }

    /** The raw source, for callers that need location or executor. */
    public CommandSourceStack source() {
        return source;
    }
}

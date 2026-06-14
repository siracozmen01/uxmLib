package com.uxplima.uxmlib.npc;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.netty.channel.Channel;

/**
 * Writes an already-built packet to a player's connection. The packet is typed {@link Object} on purpose:
 * this class stays free of any {@code net.minecraft} reference so the npc module remains NMS-clean, and the
 * caller supplies a concrete packet built elsewhere (the renderer's quarantined NMS class). Resolving the
 * channel goes through a {@link ChannelResolver}; if it cannot be resolved (a non-CraftPlayer, a closed
 * channel, an unexpected layout) the send is a no-op rather than an error.
 */
public final class PacketSender {

    private final ChannelResolver resolver;

    public PacketSender(ChannelResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /**
     * Send {@code packet} to {@code player}. If the player's Netty channel cannot be resolved, this is a
     * no-op — the resolver returns empty and nothing is written.
     */
    public void send(Player player, Object packet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(packet, "packet");
        // Fire-and-forget write; the returned ChannelFuture is intentionally not awaited.
        resolver.resolve(player).ifPresent(channel -> {
            var unused = channel.writeAndFlush(packet);
        });
    }

    /** Send {@code packet} directly to an already-resolved {@code channel}. */
    public void send(Channel channel, Object packet) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(packet, "packet");
        // Fire-and-forget write; the returned ChannelFuture is intentionally not awaited.
        var unused = channel.writeAndFlush(packet);
    }
}

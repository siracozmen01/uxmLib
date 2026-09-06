package com.uxplima.uxmlib.pipeline;

/**
 * The interception seam. A listener inspects a raw packet object (an opaque {@link Object}: this foundation
 * does not yet decode packets into typed wrappers) flowing through a player's connection channel and decides
 * whether to {@link PacketAction#PASS} or {@link PacketAction#CANCEL} it, or, on the outbound path, to
 * <em>rewrite</em> it (see {@link #onSendVerdict}).
 *
 * <p>Both callbacks run on a <b>Netty I/O thread</b>, never the main server thread. Implementations must not
 * touch the Bukkit API directly here; hand work to a {@code Scheduler} if it must run on a region thread. A
 * listener must be cheap and must not block. If a callback throws, the registry swallows it and the packet
 * passes (fail-open), so a faulty listener can never break a player's connection.
 *
 * <p>The default methods return {@link PacketAction#PASS}, so a listener only overrides the direction it
 * cares about.
 *
 * <p><strong>Pass/cancel vs rewrite.</strong> The two {@code on*} methods are the original pass/cancel seam and
 * stay the way listeners declare a verdict that only passes or cancels: the registry calls {@link #onSendVerdict}
 * /{@link #onReceiveVerdict}, which by default delegate to them, so a listener implemented only against
 * {@code onSend}/{@code onReceive} behaves exactly as before. A listener that wants to <em>replace</em> an
 * outbound packet overrides {@link #onSendVerdict} and returns {@link PacketVerdict#rewrite(Object)}; everything
 * else needs only {@code onSend}/{@code onReceive}.
 */
@FunctionalInterface
public interface PacketListener {

    /**
     * Inspect an outbound (server-to-client) packet.
     *
     * @param player the channel owner's unique id, or {@code null} before the login handshake names the
     *     connection (the channel exists before a {@code Player} does)
     * @param packet the raw packet object; never {@code null}
     * @return the verdict for this packet
     */
    PacketAction onSend(java.util.@org.jspecify.annotations.Nullable UUID player, Object packet);

    /**
     * Inspect an inbound (client-to-server) packet. Defaults to {@link PacketAction#PASS}.
     *
     * @param player the channel owner's unique id, or {@code null} before login
     * @param packet the raw packet object; never {@code null}
     * @return the verdict for this packet
     */
    default PacketAction onReceive(java.util.@org.jspecify.annotations.Nullable UUID player, Object packet) {
        return PacketAction.PASS;
    }

    /**
     * Inspect an outbound packet and decide pass, cancel, <em>or rewrite</em>. Override this (instead of
     * {@link #onSend}) to forward a replacement packet via {@link PacketVerdict#rewrite(Object)}. The default
     * delegates to {@link #onSend}, so a listener that does not rewrite needs only that method.
     *
     * @param player the channel owner's unique id, or {@code null} before login
     * @param packet the raw packet object; never {@code null}
     * @return the verdict for this packet (pass, cancel, or a rewrite carrying the replacement)
     */
    default PacketVerdict onSendVerdict(java.util.@org.jspecify.annotations.Nullable UUID player, Object packet) {
        return PacketVerdict.from(onSend(player, packet));
    }

    /**
     * Inspect an inbound packet and decide pass or cancel. Rewrite is an outbound-only mechanism, so an inbound
     * {@link PacketVerdict#rewrite(Object)} is honoured as a pass; the default delegates to {@link #onReceive}.
     *
     * @param player the channel owner's unique id, or {@code null} before login
     * @param packet the raw packet object; never {@code null}
     * @return the verdict for this packet
     */
    default PacketVerdict onReceiveVerdict(java.util.@org.jspecify.annotations.Nullable UUID player, Object packet) {
        return PacketVerdict.from(onReceive(player, packet));
    }
}

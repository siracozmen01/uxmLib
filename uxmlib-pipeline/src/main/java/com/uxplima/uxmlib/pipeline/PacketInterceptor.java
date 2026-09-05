package com.uxplima.uxmlib.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jspecify.annotations.Nullable;

/**
 * The duplex Netty handler spliced into a player's connection pipeline. On each direction it asks the
 * {@link PacketListenerRegistry} whether to pass the packet, and forwards it only if no listener cancelled. On
 * the outbound path it additionally honours a {@link PacketVerdict#rewrite(Object) rewrite}: when a listener
 * folds in a replacement packet, that replacement is written downstream in place of the original.
 *
 * <p>It is deliberately <b>fail-open</b>: the registry already swallows listener faults, and this handler
 * routes any collected faults to an injected sink (so they are logged off the I/O thread) and always forwards
 * the packet if dispatch could not produce a clean cancel. A bug in a listener can therefore never silently
 * strand a player's connection.
 *
 * <p>The owning player's id is read through an injected {@link Supplier} rather than captured at construction,
 * because the channel exists before login names the connection: early in the handshake the supplier returns
 * {@code null}, which listeners receive as the {@code player} argument.
 *
 * <p>Marked {@code @Sharable}? No: one instance is created per channel (per player), so it is not shared
 * across pipelines and needs no sharable contract.
 */
public final class PacketInterceptor extends ChannelDuplexHandler {

    private final PacketListenerRegistry registry;
    private final Supplier<@Nullable UUID> ownerId;
    private final Consumer<Throwable> faultSink;

    /**
     * @param registry the listeners to dispatch through
     * @param ownerId supplies the channel owner's id (or {@code null} pre-login)
     * @param faultSink receives any listener throwable collected during dispatch, to be logged off-thread
     */
    public PacketInterceptor(
            PacketListenerRegistry registry, Supplier<@Nullable UUID> ownerId, Consumer<Throwable> faultSink) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.faultSink = Objects.requireNonNull(faultSink, "faultSink");
    }

    /**
     * A fresh, not-yet-added copy of this handler bound to the same owner/registry/sink. A reorder must remove
     * and re-add the handler, but this type is intentionally not {@code @Sharable}, so Netty forbids re-adding
     * the same instance ({@code checkMultiplicity}). Re-anchoring therefore splices in this fresh copy.
     */
    PacketInterceptor freshCopy() {
        return new PacketInterceptor(registry, ownerId, faultSink);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (registry.isEmpty()) {
            super.write(ctx, msg, promise);
            return;
        }
        PacketListenerRegistry.Dispatch dispatch =
                report(registry.dispatch(PacketDirection.OUTBOUND, ownerId.get(), msg));
        if (dispatch.cancelled()) {
            return; // cancelled: drop the outbound packet by not forwarding it.
        }
        // A rewrite forwards the replacement in place of the original; otherwise forward the original unchanged.
        Object replacement = dispatch.replacement();
        super.write(ctx, replacement != null ? replacement : msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (passes(PacketDirection.INBOUND, msg)) {
            super.channelRead(ctx, msg);
        }
        // else: cancelled: drop the inbound packet by not forwarding it (rewrite is outbound-only, ignored here).
    }

    /** Dispatch and fold; report faults to the sink. Returns whether the packet should be forwarded. */
    private boolean passes(PacketDirection direction, Object msg) {
        if (registry.isEmpty()) {
            return true;
        }
        return !report(registry.dispatch(direction, ownerId.get(), msg)).cancelled();
    }

    /** Route any collected faults to the sink (off-thread logging) and return the dispatch unchanged. */
    private PacketListenerRegistry.Dispatch report(PacketListenerRegistry.Dispatch dispatch) {
        List<Throwable> faults = dispatch.faults();
        if (!faults.isEmpty()) {
            reportFaults(faults);
        }
        return dispatch;
    }

    private void reportFaults(List<Throwable> faults) {
        for (Throwable fault : faults) {
            try {
                faultSink.accept(fault);
            } catch (RuntimeException ignored) {
                // A faulty fault sink must never break the connection; there is nowhere safe to escalate.
            }
        }
    }
}

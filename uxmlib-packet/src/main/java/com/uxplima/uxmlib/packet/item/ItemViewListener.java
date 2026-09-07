package com.uxplima.uxmlib.packet.item;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmlib.pipeline.PacketAction;
import com.uxplima.uxmlib.pipeline.PacketListener;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.Nullable;

/**
 * The outbound listener that puts an {@link ItemView} in front of every item a client is sent.
 *
 * <p>It holds no packet knowledge of its own: {@link ItemPackets} answers what a packet carries and builds
 * the replacement, and this class only decides when to ask. It never cancels a packet, and it rewrites one
 * only when the view actually changed an item, so a server with a view that decorates nothing pays a type
 * test per outbound packet and nothing else.
 *
 * <p>Runs on a Netty I/O thread. See {@link ItemView} for what that forbids.
 */
public final class ItemViewListener implements PacketListener {

    private final ItemPackets packets;
    private final ItemView view;

    public ItemViewListener(ItemPackets packets, ItemView view) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.view = Objects.requireNonNull(view, "view");
    }

    /**
     * Never used to decide anything: this listener rewrites rather than cancels, so the pass/cancel half of
     * the seam always passes and {@link #onSendVerdict} carries the real answer.
     */
    @Override
    public PacketAction onSend(@Nullable UUID player, Object packet) {
        return PacketAction.PASS;
    }

    @Override
    public PacketVerdict onSendVerdict(@Nullable UUID player, Object packet) {
        Objects.requireNonNull(packet, "packet");
        if (player == null || !packets.carriesItems(packet)) {
            // Before login there is no viewer to draw for, and most outbound packets carry no item at all.
            return PacketVerdict.pass();
        }
        UUID viewer = player;
        Object replacement = packets.withItems(packet, real -> view.shownTo(viewer, real));
        return replacement == null ? PacketVerdict.pass() : PacketVerdict.rewrite(replacement);
    }
}

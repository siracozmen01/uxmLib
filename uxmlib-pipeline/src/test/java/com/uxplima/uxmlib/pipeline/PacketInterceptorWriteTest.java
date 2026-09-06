package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import io.netty.channel.embedded.EmbeddedChannel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Drives the {@link PacketInterceptor} write path against an {@link EmbeddedChannel} to prove the three outbound
 * verdicts: a pass forwards the original packet unchanged, a cancel drops it, and a rewrite forwards the
 * listener's replacement in its place. An {@code EmbeddedChannel}'s {@code outboundMessages()} captures exactly
 * what the handler wrote downstream, so the test reads the forwarded packet directly with no live server.
 */
class PacketInterceptorWriteTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static EmbeddedChannel channelWith(PacketListenerRegistry registry) {
        return new EmbeddedChannel(new PacketInterceptor(registry, () -> OWNER, fault -> {}));
    }

    @Test
    void aPassForwardsTheOriginalPacketUnchanged() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register((id, packet) -> PacketAction.PASS);
        EmbeddedChannel channel = channelWith(registry);
        Object original = "original";

        channel.writeOutbound(original);

        assertThat(channel.outboundMessages()).containsExactly(original);
    }

    @Test
    void anEmptyRegistryForwardsTheOriginalPacket() {
        EmbeddedChannel channel = channelWith(new PacketListenerRegistry());
        Object original = "original";

        channel.writeOutbound(original);

        assertThat(channel.outboundMessages()).containsExactly(original);
    }

    @Test
    void aCancelDropsTheOutboundPacket() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register((id, packet) -> PacketAction.CANCEL);
        EmbeddedChannel channel = channelWith(registry);

        channel.writeOutbound("dropped");

        assertThat(channel.outboundMessages()).isEmpty();
    }

    @Test
    void aRewriteForwardsTheReplacementInPlaceOfTheOriginal() {
        Object replacement = "replacement";
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register(new PacketListener() {
            @Override
            public PacketAction onSend(@Nullable UUID player, Object packet) {
                return PacketAction.PASS;
            }

            @Override
            public PacketVerdict onSendVerdict(@Nullable UUID player, Object packet) {
                return PacketVerdict.rewrite(replacement);
            }
        });
        EmbeddedChannel channel = channelWith(registry);

        channel.writeOutbound("original");

        assertThat(channel.outboundMessages()).containsExactly(replacement);
    }
}

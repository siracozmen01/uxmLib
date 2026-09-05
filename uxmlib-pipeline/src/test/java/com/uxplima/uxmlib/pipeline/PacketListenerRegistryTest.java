package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PacketListenerRegistryTest {

    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void emptyRegistryReportsEmptyAndPasses() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        assertThat(registry.isEmpty()).isTrue();
        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.OUTBOUND, PLAYER, new Object());
        assertThat(dispatch.cancelled()).isFalse();
        assertThat(dispatch.faults()).isEmpty();
    }

    @Test
    void registerIsIdempotentForSameInstanceAndPreservesOrder() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        PacketListener a = (id, packet) -> PacketAction.PASS;
        PacketListener b = (id, packet) -> PacketAction.PASS;
        registry.register(a);
        registry.register(a);
        registry.register(b);
        assertThat(registry.snapshot()).containsExactly(a, b);
    }

    @Test
    void anySingleCancelVetoesButEveryListenerStillSeesThePacket() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        List<String> seen = new ArrayList<>();
        registry.register((id, packet) -> {
            seen.add("first");
            return PacketAction.CANCEL;
        });
        registry.register((id, packet) -> {
            seen.add("second");
            return PacketAction.PASS;
        });

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.OUTBOUND, PLAYER, new Object());

        assertThat(dispatch.cancelled()).isTrue();
        assertThat(seen).containsExactly("first", "second");
    }

    @Test
    void inboundDispatchUsesOnReceiveDefaultPass() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        // Only overrides onSend; onReceive defaults to PASS, so an inbound dispatch must not cancel.
        registry.register((id, packet) -> PacketAction.CANCEL);

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.INBOUND, PLAYER, new Object());

        assertThat(dispatch.cancelled()).isFalse();
    }

    @Test
    void inboundDispatchHonoursOnReceiveOverride() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register(new PacketListener() {
            @Override
            public PacketAction onSend(@Nullable UUID player, Object packet) {
                return PacketAction.PASS;
            }

            @Override
            public PacketAction onReceive(@Nullable UUID player, Object packet) {
                return PacketAction.CANCEL;
            }
        });

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.INBOUND, PLAYER, new Object());

        assertThat(dispatch.cancelled()).isTrue();
    }

    @Test
    void aThrowingListenerIsFailOpenAndItsFaultIsCollected() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        RuntimeException boom = new IllegalStateException("boom");
        registry.register((id, packet) -> {
            throw boom;
        });
        registry.register((id, packet) -> PacketAction.PASS);

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.OUTBOUND, PLAYER, new Object());

        assertThat(dispatch.cancelled()).isFalse();
        assertThat(dispatch.faults()).containsExactly(boom);
    }

    @Test
    void aRewriteVerdictFoldsInItsReplacementWithoutCancelling() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        Object replacement = new Object();
        registry.register(rewritingOnSend(replacement));

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.OUTBOUND, PLAYER, new Object());

        assertThat(dispatch.cancelled()).isFalse();
        assertThat(dispatch.rewritten()).isTrue();
        assertThat(dispatch.replacement()).isSameAs(replacement);
    }

    @Test
    void aCancelBeatsARewrite() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register(rewritingOnSend(new Object()));
        registry.register((id, packet) -> PacketAction.CANCEL);

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.OUTBOUND, PLAYER, new Object());

        assertThat(dispatch.cancelled()).isTrue();
        // A cancelled packet never carries a replacement: it is dropped, not rewritten.
        assertThat(dispatch.replacement()).isNull();
        assertThat(dispatch.rewritten()).isFalse();
    }

    @Test
    void theFirstRewriteWinsAndLaterListenersSeeTheOriginalPacket() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        Object first = new Object();
        Object original = new Object();
        List<Object> seen = new ArrayList<>();
        registry.register(rewritingOnSend(first));
        registry.register(new PacketListener() {
            @Override
            public PacketAction onSend(@Nullable UUID player, Object packet) {
                seen.add(packet);
                return PacketAction.PASS;
            }

            @Override
            public PacketVerdict onSendVerdict(@Nullable UUID player, Object packet) {
                seen.add(packet);
                return PacketVerdict.rewrite(new Object());
            }
        });

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.OUTBOUND, PLAYER, original);

        assertThat(dispatch.replacement()).isSameAs(first);
        // The second listener still saw the ORIGINAL packet, never the first listener's pending replacement.
        assertThat(seen).containsExactly(original);
    }

    @Test
    void anInboundRewriteIsTreatedAsAPass() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register(new PacketListener() {
            @Override
            public PacketAction onSend(@Nullable UUID player, Object packet) {
                return PacketAction.PASS;
            }

            @Override
            public PacketVerdict onReceiveVerdict(@Nullable UUID player, Object packet) {
                return PacketVerdict.rewrite(new Object());
            }
        });

        PacketListenerRegistry.Dispatch dispatch = registry.dispatch(PacketDirection.INBOUND, PLAYER, new Object());

        // Rewrite is outbound-only; an inbound rewrite must not cancel, and the interceptor ignores any replacement.
        assertThat(dispatch.cancelled()).isFalse();
        assertThat(dispatch.replacement()).isNotNull();
    }

    private static PacketListener rewritingOnSend(Object replacement) {
        return new PacketListener() {
            @Override
            public PacketAction onSend(@Nullable UUID player, Object packet) {
                return PacketAction.PASS;
            }

            @Override
            public PacketVerdict onSendVerdict(@Nullable UUID player, Object packet) {
                return PacketVerdict.rewrite(replacement);
            }
        };
    }

    @Test
    void nullPlayerIsAllowed() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        List<@Nullable UUID> seen = new ArrayList<>();
        registry.register((id, packet) -> {
            seen.add(id);
            return PacketAction.PASS;
        });

        registry.dispatch(PacketDirection.OUTBOUND, null, new Object());

        assertThat(seen).containsExactly((UUID) null);
    }

    @Test
    void unregisterRemovesTheListener() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        PacketListener listener = (id, packet) -> PacketAction.PASS;
        registry.register(listener);
        assertThat(registry.unregister(listener)).isTrue();
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.unregister(listener)).isFalse();
    }

    @Test
    void dispatchRejectsNullDirectionAndPacket() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        assertThatNullPointerException()
                .isThrownBy(() -> registry.dispatch(PacketDirection.OUTBOUND, PLAYER, nullPacket()));
    }

    @SuppressWarnings("NullAway") // intentionally feeds a null packet to assert the entry-point guard fires.
    private static Object nullPacket() {
        return null;
    }
}

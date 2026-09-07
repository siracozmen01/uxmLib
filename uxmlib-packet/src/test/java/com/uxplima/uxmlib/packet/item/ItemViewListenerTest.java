package com.uxplima.uxmlib.packet.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.pipeline.PacketAction;
import com.uxplima.uxmlib.pipeline.PacketDirection;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * What the outbound listener decides, driven against a fake packet port.
 *
 * <p>The two properties that matter are here: an item nobody wanted to change forwards the original packet
 * rather than a copy of it, and a view that throws leaves the connection alone.
 */
class ItemViewListenerTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final FakeItemPackets packets = new FakeItemPackets();

    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a packet that carries no item is never decoded")
    void aPacketWithoutAnItemPasses() {
        ItemViewListener listener = new ItemViewListener(packets, (viewer, real) -> real);

        PacketVerdict verdict = listener.onSendVerdict(VIEWER, "a packet with no item in it");

        assertThat(verdict.cancels()).isFalse();
        assertThat(verdict.replacement()).isNull();
        assertThat(packets.asked()).isEmpty();
    }

    @Test
    @DisplayName("before login there is no viewer, so the view is never asked")
    void beforeLoginTheViewIsNotAsked() {
        List<UUID> seen = new ArrayList<>();
        ItemViewListener listener = new ItemViewListener(packets, (viewer, real) -> {
            seen.add(viewer);
            return real;
        });

        PacketVerdict verdict = listener.onSendVerdict(null, new FakeItemPackets.Slot(sword()));

        assertThat(verdict.replacement()).isNull();
        assertThat(seen).isEmpty();
        assertThat(packets.asked()).isEmpty();
    }

    @Test
    @DisplayName("an item the view leaves alone forwards the original packet, not a copy")
    void anUntouchedItemPasses() {
        ItemViewListener listener = new ItemViewListener(packets, (viewer, real) -> real);
        FakeItemPackets.Slot packet = new FakeItemPackets.Slot(sword());

        PacketVerdict verdict = listener.onSendVerdict(VIEWER, packet);

        assertThat(verdict.replacement()).isNull();
        assertThat(verdict.cancels()).isFalse();
        assertThat(packets.asked()).containsExactly(packet);
    }

    @Test
    @DisplayName("an item the view changed comes back as a rewrite carrying the new packet")
    void aChangedItemIsRewritten() {
        ItemViewListener listener = new ItemViewListener(packets, (viewer, real) -> decorated(real));
        FakeItemPackets.Slot packet = new FakeItemPackets.Slot(sword());

        PacketVerdict verdict = listener.onSendVerdict(VIEWER, packet);

        assertThat(verdict.cancels()).isFalse();
        assertThat(verdict.replacement()).isInstanceOf(FakeItemPackets.Slot.class);
        FakeItemPackets.Slot drawn =
                (FakeItemPackets.Slot) Objects.requireNonNull(verdict.replacement(), "replacement");
        assertThat(drawn.item().lore()).isNotNull();
        assertThat(packet.item().lore())
                .describedAs("the packet the server built is not touched")
                .isNull();
    }

    @Test
    @DisplayName("the view is told which viewer the packet is on its way to")
    void theViewerReachesTheView() {
        List<UUID> seen = new ArrayList<>();
        ItemViewListener listener = new ItemViewListener(packets, (viewer, real) -> {
            seen.add(viewer);
            return real;
        });

        listener.onSendVerdict(VIEWER, new FakeItemPackets.Slot(sword()));

        assertThat(seen).containsExactly(VIEWER);
    }

    @Test
    @DisplayName("the pass and cancel half of the seam never cancels")
    void thePassCancelHalfAlwaysPasses() {
        ItemViewListener listener = new ItemViewListener(packets, (viewer, real) -> decorated(real));

        assertThat(listener.onSend(VIEWER, new FakeItemPackets.Slot(sword()))).isEqualTo(PacketAction.PASS);
    }

    @Test
    @DisplayName("a view that throws leaves the packet alone and hands the fault to the caller")
    void aBrokenViewFailsOpen() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register(new ItemViewListener(packets, (viewer, real) -> {
            throw new IllegalStateException("the view is broken");
        }));

        PacketListenerRegistry.Dispatch dispatch =
                registry.dispatch(PacketDirection.OUTBOUND, VIEWER, new FakeItemPackets.Slot(sword()));

        assertThat(dispatch.cancelled()).isFalse();
        assertThat(dispatch.rewritten()).isFalse();
        assertThat(dispatch.faults()).hasSize(1);
    }

    @Test
    @DisplayName("a rewrite folds through the registry the interceptor actually reads")
    void aRewriteFoldsThroughTheRegistry() {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        registry.register(new ItemViewListener(packets, (viewer, real) -> decorated(real)));

        PacketListenerRegistry.Dispatch dispatch =
                registry.dispatch(PacketDirection.OUTBOUND, VIEWER, new FakeItemPackets.Slot(sword()));

        assertThat(dispatch.cancelled()).isFalse();
        assertThat(dispatch.rewritten()).isTrue();
    }

    private static ItemStack sword() {
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    private static ItemStack decorated(ItemStack real) {
        ItemStack copy = real.clone();
        copy.lore(List.of(Component.text("sharpened III")));
        return copy;
    }
}

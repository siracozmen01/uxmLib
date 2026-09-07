package com.uxplima.uxmlib.packet.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The hologram that is sent and never spawned.
 *
 * <p>The first assertion of the set is the one the whole class exists for: after a frame has been drawn, the
 * world still holds no entity. Everything else here guards the behaviour that makes that useful: the text
 * reaches only the players who are near, it goes away when they walk off, and it is rebuilt for a client only
 * when the client cannot be repainted instead.
 */
class PacketHologramTest {

    private ServerMock server;
    private World world;
    private Location anchor;
    private FakeHologramPackets packets;
    private FakeRegionScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        anchor = new Location(world, 0, 64, 0);
        packets = new FakeHologramPackets();
        scheduler = new FakeRegionScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the server holds no entity for a hologram that has already been drawn")
    void theServerHoldsNoEntity() {
        Player alice = playerAt("Alice", 1, 64, 1);

        PacketHologram hologram = show(viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();

        assertThat(hologram.viewerCount()).isEqualTo(1);
        assertThat(packets.packetsFor(alice)).hasSize(1);
        // The only thing the world holds is the player looking at the hologram. Nothing was spawned for it.
        assertThat(world.getEntities()).containsExactly(alice);
    }

    @Test
    @DisplayName("a viewer in range is sent one spawn and one metadata packet per line, in a single frame")
    void aNearViewerIsSentTheWholeStackAtOnce() {
        Player alice = playerAt("Alice", 1, 64, 1);

        show(viewer -> List.of(Component.text("top"), Component.text("bottom")));
        scheduler.firstFrame();

        FakeHologramPackets.Bundle frame =
                (FakeHologramPackets.Bundle) packets.packetsFor(alice).get(0);
        assertThat(frame.packets()).hasSize(4);
        assertThat(frame.packets().get(0)).isInstanceOf(FakeHologramPackets.Spawn.class);
        assertThat(frame.packets().get(1)).isInstanceOf(FakeHologramPackets.Metadata.class);
        FakeHologramPackets.Spawn spawn =
                (FakeHologramPackets.Spawn) frame.packets().get(0);
        assertThat(spawn.x()).isEqualTo(0.0);
        assertThat(spawn.y()).isEqualTo(64.0);
        assertThat(spawn.z()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("the first line of a stack sits above the last")
    void theFirstLineSitsHighest() {
        Player alice = playerAt("Alice", 1, 64, 1);

        show(viewer -> List.of(Component.text("top"), Component.text("bottom")));
        scheduler.firstFrame();

        FakeHologramPackets.Bundle frame =
                (FakeHologramPackets.Bundle) packets.packetsFor(alice).get(0);
        FakeHologramPackets.Metadata top =
                (FakeHologramPackets.Metadata) frame.packets().get(1);
        FakeHologramPackets.Metadata bottom =
                (FakeHologramPackets.Metadata) frame.packets().get(3);
        assertThat(top.text()).isEqualTo(Component.text("top"));
        assertThat(bottom.text()).isEqualTo(Component.text("bottom"));
        assertThat(top.translation().y()).isGreaterThan(bottom.translation().y());
    }

    @Test
    @DisplayName("a player beyond the view distance is sent nothing")
    void aFarPlayerIsSentNothing() {
        playerAt("Alice", 500, 64, 500);

        PacketHologram hologram = show(viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();

        assertThat(packets.sends).isEmpty();
        assertThat(hologram.viewerCount()).isZero();
    }

    @Test
    @DisplayName("with nobody near, the hologram writes no packet at all")
    void nobodyNearMeansNoPacket() {
        PacketHologram hologram = show(viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();
        scheduler.tick();
        scheduler.tick();

        assertThat(packets.sends).isEmpty();
        assertThat(hologram.viewerCount()).isZero();
    }

    @Test
    @DisplayName("a viewer who walks out of range is sent a remove and then nothing")
    void aDepartingViewerIsRemoved() {
        Player alice = playerAt("Alice", 1, 64, 1);
        PacketHologram hologram = show(viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();
        packets.forget();

        alice.teleport(new Location(world, 400, 64, 400));
        scheduler.tick();

        assertThat(packets.packetsFor(alice)).hasSize(1);
        assertThat(packets.packetsFor(alice).get(0)).isInstanceOf(FakeHologramPackets.Remove.class);
        assertThat(hologram.viewerCount()).isZero();

        packets.forget();
        scheduler.tick();
        assertThat(packets.sends).isEmpty();
    }

    @Test
    @DisplayName("a viewer who comes into range later is sent the spawn frame then")
    void anArrivingViewerIsSpawnedLater() {
        Player alice = playerAt("Alice", 400, 64, 400);
        show(viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();
        assertThat(packets.sends).isEmpty();

        alice.teleport(new Location(world, 2, 64, 2));
        scheduler.tick();

        assertThat(packets.packetsFor(alice)).hasSize(1);
        assertThat(packets.packetsFor(alice).get(0)).isInstanceOf(FakeHologramPackets.Bundle.class);
    }

    @Test
    @DisplayName("two viewers of one hologram can be sent different text")
    void textIsResolvedPerViewer() {
        Player alice = playerAt("Alice", 1, 64, 1);
        Player bob = playerAt("Bob", 2, 64, 2);

        show(viewer -> List.of(Component.text("hello " + viewer.getName())));
        scheduler.firstFrame();

        assertThat(lineOf(packets.packetsFor(alice).get(0))).isEqualTo(Component.text("hello Alice"));
        assertThat(lineOf(packets.packetsFor(bob).get(0))).isEqualTo(Component.text("hello Bob"));
    }

    @Test
    @DisplayName("a viewer who stays is repainted rather than respawned, and keeps the same entity ids")
    void aStayingViewerIsRepainted() {
        Player alice = playerAt("Alice", 1, 64, 1);
        show(viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();
        int allocated = packets.allocations();
        packets.forget();

        scheduler.tick();

        assertThat(packets.packetsFor(alice)).hasSize(1);
        assertThat(packets.packetsFor(alice).get(0)).isInstanceOf(FakeHologramPackets.Metadata.class);
        assertThat(packets.allocations()).isEqualTo(allocated);
    }

    @Test
    @DisplayName("a line count that changes takes the old stack down before the new one goes up")
    void aChangedLineCountRespawns() {
        Player alice = playerAt("Alice", 1, 64, 1);
        PacketHologram hologram = show(viewer -> List.of(Component.text("one")));
        scheduler.firstFrame();
        packets.forget();

        hologram.setText(viewer -> List.of(Component.text("one"), Component.text("two")));
        scheduler.tick();

        List<Object> written = packets.packetsFor(alice);
        assertThat(written).hasSize(2);
        assertThat(written.get(0)).isInstanceOf(FakeHologramPackets.Remove.class);
        assertThat(written.get(1)).isInstanceOf(FakeHologramPackets.Bundle.class);
    }

    @Test
    @DisplayName("remove takes the hologram off every viewer and stops the refresh")
    void removeClearsEveryViewerAndStopsTheLoop() {
        Player alice = playerAt("Alice", 1, 64, 1);
        PacketHologram hologram = show(viewer -> List.of(Component.text("shop")));
        scheduler.firstFrame();
        packets.forget();

        hologram.remove();

        assertThat(packets.packetsFor(alice)).hasSize(1);
        assertThat(packets.packetsFor(alice).get(0)).isInstanceOf(FakeHologramPackets.Remove.class);
        assertThat(scheduler.cancelled()).isTrue();
        assertThat(hologram.viewerCount()).isZero();

        packets.forget();
        hologram.remove();
        scheduler.tick();
        assertThat(packets.sends).isEmpty();
    }

    @Test
    @DisplayName("the refresh runs on the anchor's own region")
    void theRefreshRunsOnTheAnchorRegion() {
        show(viewer -> List.of(Component.text("shop")));

        assertThat(scheduler.hasTimer()).isTrue();
        assertThat(scheduler.timerLocation()).isEqualTo(anchor);
    }

    @Test
    @DisplayName("text that returns no line is refused, rather than sending an empty frame")
    void emptyTextIsRefused() {
        playerAt("Alice", 1, 64, 1);
        show(viewer -> List.of());

        assertThatThrownBy(() -> scheduler.firstFrame())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no lines");
    }

    @Test
    @DisplayName("a view distance of zero or less is refused")
    void aViewDistanceOfZeroIsRefused() {
        assertThatThrownBy(() -> PacketHologram.show(
                        packets,
                        scheduler,
                        anchor,
                        HologramAppearance.defaults(),
                        viewer -> List.of(Component.text("shop")),
                        0.0,
                        PacketHologram.DEFAULT_REFRESH_PERIOD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("viewDistance");
    }

    private PacketHologram show(java.util.function.Function<Player, List<Component>> text) {
        return PacketHologram.show(packets, scheduler, anchor, HologramAppearance.defaults(), text);
    }

    private Player playerAt(String name, double x, double y, double z) {
        Player player = server.addPlayer(name);
        player.teleport(new Location(world, x, y, z));
        return player;
    }

    /** The text of the first metadata packet inside a spawn frame. */
    private static Component lineOf(Object frame) {
        FakeHologramPackets.Bundle bundle = (FakeHologramPackets.Bundle) frame;
        return ((FakeHologramPackets.Metadata) bundle.packets().get(1)).text();
    }
}

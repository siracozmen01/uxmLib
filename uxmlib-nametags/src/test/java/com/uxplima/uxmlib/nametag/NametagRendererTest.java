package com.uxplima.uxmlib.nametag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Drives the renderer against a recording {@link FakeNametagPackets} and a controllable {@link FakeScheduler}.
 * No NMS is involved: the port returns sentinel records, so the test asserts exactly which packet structure
 * reached which viewer and that the refresh task is wired and cancelled.
 */
class NametagRendererTest {

    private ServerMock server;
    private FakeNametagPackets packets;
    private FakeScheduler scheduler;
    private NametagRenderer renderer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        packets = new FakeNametagPackets();
        scheduler = new FakeScheduler();
        renderer = new NametagRenderer(packets, scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void constructorRejectsNullDependencies() {
        assertThatNullPointerException().isThrownBy(() -> new NametagRenderer(nullPackets(), scheduler));
        assertThatNullPointerException().isThrownBy(() -> new NametagRenderer(packets, nullScheduler()));
    }

    @Test
    void showAllocatesOneIdAndSendsASpawnBundleToEachOnlineViewer() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        Player b = server.addPlayer("Bob");

        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId(), b.getUniqueId()), v -> line("x"));

        assertThat(packets.allocations()).isEqualTo(1);
        assertThat(bundlesTo(a)).hasSize(1);
        assertThat(bundlesTo(b)).hasSize(1);
        FakeNametagPackets.Bundle bundle = bundlesTo(a).get(0);
        assertThat(bundle.packets())
                .hasSize(3)
                .hasOnlyElementsOfTypes(
                        FakeNametagPackets.Spawn.class,
                        FakeNametagPackets.Metadata.class,
                        FakeNametagPackets.Mount.class);
    }

    @Test
    void offlineViewerInTheSetIsSkippedWithNoSendAndNoThrow() {
        Player target = server.addPlayer("Target");
        Player online = server.addPlayer("Online");
        UUID offline = UUID.randomUUID();

        assertThatCode(() -> renderer.show(
                        target, Appearance.defaults(), Set.of(online.getUniqueId(), offline), v -> line("x")))
                .doesNotThrowAnyException();

        assertThat(packets.sends).allMatch(s -> s.viewer().getUniqueId().equals(online.getUniqueId()));
        assertThat(bundlesTo(online)).hasSize(1);
    }

    @Test
    void eachViewerSeesItsOwnComponentInItsMetadataPacket() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        Player b = server.addPlayer("Bob");
        PerViewerText perViewer = viewer -> List.of(Component.text("hi " + viewer));

        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId(), b.getUniqueId()), perViewer);

        assertThat(metadataTextTo(a)).isEqualTo(Component.text("hi " + a.getUniqueId()));
        assertThat(metadataTextTo(b)).isEqualTo(Component.text("hi " + b.getUniqueId()));
    }

    @Test
    void metadataPacketCarriesFullOpacity() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");

        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), v -> line("x"));

        assertThat(metadataTo(a).opacity()).isEqualTo(Appearance.FULL_OPACITY);
    }

    @Test
    void updateSpawnsNewViewerRemovesDepartedAndRefreshesRemaining() {
        Player target = server.addPlayer("Target");
        Player stay = server.addPlayer("Stay");
        Player leave = server.addPlayer("Leave");
        Player join = server.addPlayer("Join");
        NametagHandle handle = renderer.show(
                target, Appearance.defaults(), Set.of(stay.getUniqueId(), leave.getUniqueId()), v -> line("x"));
        packets.sends.clear();

        handle.update(Set.of(stay.getUniqueId(), join.getUniqueId()), v -> line("y"), Appearance.defaults());

        // newcomer gets a full spawn bundle
        assertThat(bundlesTo(join)).hasSize(1);
        // departed viewer gets a remove packet
        assertThat(removesTo(leave)).hasSize(1);
        // remaining viewer gets a metadata refresh, no second spawn
        assertThat(bundlesTo(stay)).isEmpty();
        assertThat(metadataPacketsTo(stay)).hasSize(1);
    }

    @Test
    void entityIdIsStableAcrossUpdates() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), v -> line("x"));
        int spawnId = ((FakeNametagPackets.Spawn) bundlesTo(a).get(0).packets().get(0)).entityId();
        packets.sends.clear();

        scheduler.tick();

        // The refresh sends a standalone metadata packet (a is a remaining viewer), not a fresh spawn bundle.
        assertThat(packets.allocations()).isEqualTo(1);
        assertThat(metadataPacketsTo(a)).hasSize(1);
        assertThat(metadataPacketsTo(a).get(0).entityId()).isEqualTo(spawnId);
    }

    @Test
    void refreshTaskTargetsTheNametagOwner() {
        Player target = server.addPlayer("Target");
        renderer.show(target, Appearance.defaults(), Set.of(), v -> line("x"));

        assertThat(scheduler.hasTimer()).isTrue();
        assertThat(scheduler.timerEntity()).isSameAs(target);
    }

    @Test
    void removeSendsARemovePacketToEveryViewerAndCancelsTheRefreshTask() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        Player b = server.addPlayer("Bob");
        NametagHandle handle =
                renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId(), b.getUniqueId()), v -> line("x"));
        packets.sends.clear();

        handle.remove();

        assertThat(removesTo(a)).hasSize(1);
        assertThat(removesTo(b)).hasSize(1);
        assertThat(scheduler.cancelled()).isTrue();
    }

    @Test
    void removeIsIdempotent() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        NametagHandle handle = renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), v -> line("x"));
        handle.remove();
        packets.sends.clear();

        assertThatCode(handle::remove).doesNotThrowAnyException();
        assertThat(packets.sends).isEmpty();
    }

    // --- multi-line --------------------------------------------------------------------------------------

    @Test
    void threeLineTextSpawnsThreeDisplaysAndOneMountWithAllIds() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");

        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), threeLines());

        // Three distinct entity ids were allocated for the stack.
        assertThat(packets.allocations()).isEqualTo(3);
        List<Object> frame = bundlesTo(a).get(0).packets();
        assertThat(frame.stream().filter(FakeNametagPackets.Spawn.class::isInstance))
                .hasSize(3);
        assertThat(frame.stream().filter(FakeNametagPackets.Metadata.class::isInstance))
                .hasSize(3);
        List<FakeNametagPackets.Mount> mounts = frame.stream()
                .filter(FakeNametagPackets.Mount.class::isInstance)
                .map(FakeNametagPackets.Mount.class::cast)
                .toList();
        assertThat(mounts).hasSize(1);
        assertThat(mounts.get(0).passengerIds()).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    void stackedLinesCarryDescendingYTranslationTopLineHighest() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");

        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), threeLines());

        List<FakeNametagPackets.Metadata> meta = metadataInBundleTo(a);
        // The bundle is built top line first, so index 0 must sit highest and Y must strictly decrease.
        assertThat(meta).hasSize(3);
        float top = meta.get(0).translation().y();
        float mid = meta.get(1).translation().y();
        float bottom = meta.get(2).translation().y();
        assertThat(top).isGreaterThan(mid);
        assertThat(mid).isGreaterThan(bottom);
        // Even step between adjacent lines.
        assertThat(top - mid).isEqualTo(mid - bottom);
    }

    @Test
    void lineIdsAreStableAcrossUpdatesWhenLineCountIsUnchanged() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), threeLines());
        List<Integer> spawnIds = metadataInBundleTo(a).stream()
                .map(FakeNametagPackets.Metadata::entityId)
                .toList();
        packets.sends.clear();

        scheduler.tick();

        // A refresh of an unchanged 3-line stack re-sends three standalone metadata packets on the same ids.
        List<Integer> refreshIds = metadataPacketsTo(a).stream()
                .map(FakeNametagPackets.Metadata::entityId)
                .toList();
        assertThat(packets.allocations()).isEqualTo(3);
        assertThat(refreshIds).containsExactlyElementsOf(spawnIds);
    }

    @Test
    void lineCountChangeOnUpdateRemovesOldIdsAndRespawns() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        NametagHandle handle = renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), threeLines());
        packets.sends.clear();

        handle.update(Set.of(a.getUniqueId()), v -> line("only"), Appearance.defaults());

        // The shrink tears down the three old line ids, then respawns the new single-line stack.
        assertThat(removesTo(a)).hasSize(1);
        assertThat(removesTo(a).get(0).entityIds()).hasSize(3);
        assertThat(bundlesTo(a)).hasSize(1);
        assertThat(metadataInBundleTo(a)).hasSize(1);
    }

    // --- dynamic viewer supplier -------------------------------------------------------------------------

    @Test
    void supplierGainingAViewerBetweenTicksSpawnsForTheNewcomerWithoutAPush() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        Player late = server.addPlayer("Late");
        MutableViewers viewers = new MutableViewers(Set.of(a.getUniqueId()));
        renderer.show(target, Appearance.defaults(), viewers, v -> line("x"));
        packets.sends.clear();

        // The newcomer joins the supplied set; the loop must notice on the next tick on its own.
        viewers.set(Set.of(a.getUniqueId(), late.getUniqueId()));
        scheduler.tick();

        assertThat(bundlesTo(late)).hasSize(1);
        assertThat(bundlesTo(a)).isEmpty();
        assertThat(metadataPacketsTo(a)).hasSize(1);
    }

    @Test
    void supplierLosingAViewerBetweenTicksRemovesTheDepartedWithoutAPush() {
        Player target = server.addPlayer("Target");
        Player stay = server.addPlayer("Stay");
        Player leave = server.addPlayer("Leave");
        MutableViewers viewers = new MutableViewers(Set.of(stay.getUniqueId(), leave.getUniqueId()));
        renderer.show(target, Appearance.defaults(), viewers, v -> line("x"));
        packets.sends.clear();

        // The viewer drops out of the supplied set; the loop must send it a remove on the next tick.
        viewers.set(Set.of(stay.getUniqueId()));
        scheduler.tick();

        assertThat(removesTo(leave)).hasSize(1);
        assertThat(metadataPacketsTo(stay)).hasSize(1);
    }

    /** A supplier whose returned set a test can swap between ticks, mimicking players entering/leaving range. */
    private static final class MutableViewers implements java.util.function.Supplier<Set<UUID>> {
        private volatile Set<UUID> current;

        MutableViewers(Set<UUID> initial) {
            this.current = initial;
        }

        void set(Set<UUID> next) {
            this.current = next;
        }

        @Override
        public Set<UUID> get() {
            return current;
        }
    }

    // --- animation ---------------------------------------------------------------------------------------

    @Test
    void refreshTickReResolvesTextAndReSendsMetadataForUnchangedViewerSet() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        AtomicInteger calls = new AtomicInteger();
        PerViewerText animated = v -> List.of(Component.text("frame-" + calls.incrementAndGet()));
        renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId()), animated);
        int callsAfterShow = calls.get();
        packets.sends.clear();

        scheduler.tick();

        // The provider was re-invoked on the tick, and the new component reached the viewer as metadata.
        assertThat(calls.get()).isGreaterThan(callsAfterShow);
        assertThat(metadataPacketsTo(a)).hasSize(1);
        assertThat(metadataPacketsTo(a).get(0).text()).isEqualTo(Component.text("frame-" + calls.get()));
    }

    // --- line of sight -----------------------------------------------------------------------------------

    @Test
    void obstructedViewerFadesWhileClearViewerStaysFullOpacityWhenHideThroughBlocksOn() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        Player b = server.addPlayer("Bob");
        FakeLineOfSight los = new FakeLineOfSight().obstruct(a.getUniqueId());
        NametagRenderer fading = new NametagRenderer(packets, scheduler, los);
        Appearance hiding = hideThroughBlocks(true);

        fading.show(target, hiding, Set.of(a.getUniqueId(), b.getUniqueId()), v -> line("x"));

        assertThat(metadataTo(a).opacity()).isEqualTo(hiding.obscuredOpacity());
        assertThat(metadataTo(b).opacity()).isEqualTo(Appearance.FULL_OPACITY);
    }

    @Test
    void lineOfSightIsIgnoredWhenHideThroughBlocksOff() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        FakeLineOfSight los = new FakeLineOfSight().obstruct(a.getUniqueId());
        NametagRenderer fading = new NametagRenderer(packets, scheduler, los);

        fading.show(target, hideThroughBlocks(false), Set.of(a.getUniqueId()), v -> line("x"));

        assertThat(metadataTo(a).opacity()).isEqualTo(Appearance.FULL_OPACITY);
    }

    @Test
    void removeRemovesEveryLineIdForEveryViewer() {
        Player target = server.addPlayer("Target");
        Player a = server.addPlayer("Alice");
        Player b = server.addPlayer("Bob");
        NametagHandle handle =
                renderer.show(target, Appearance.defaults(), Set.of(a.getUniqueId(), b.getUniqueId()), threeLines());
        packets.sends.clear();

        handle.remove();

        assertThat(removesTo(a)).hasSize(1);
        assertThat(removesTo(a).get(0).entityIds()).hasSize(3);
        assertThat(removesTo(b)).hasSize(1);
        assertThat(removesTo(b).get(0).entityIds()).hasSize(3);
    }

    // --- helpers -----------------------------------------------------------------------------------------

    private static List<Component> line(String text) {
        return List.of(Component.text(text));
    }

    private static PerViewerText threeLines() {
        return v -> List.of(Component.text("top"), Component.text("mid"), Component.text("bottom"));
    }

    /** A defaults-shaped appearance with the line-of-sight fade flag toggled. */
    private static Appearance hideThroughBlocks(boolean hide) {
        Appearance d = Appearance.defaults();
        return new Appearance(
                d.billboard(),
                d.backgroundArgb(),
                d.textShadow(),
                d.seeThrough(),
                d.alignment(),
                d.lineWidth(),
                d.viewRange(),
                d.translation(),
                d.scale(),
                d.interpolationDurationTicks(),
                hide,
                d.obscuredOpacity());
    }

    /** The metadata packets carried inside the spawn bundle this viewer received, in bundle order. */
    private List<FakeNametagPackets.Metadata> metadataInBundleTo(Player viewer) {
        return bundlesTo(viewer).get(0).packets().stream()
                .filter(FakeNametagPackets.Metadata.class::isInstance)
                .map(FakeNametagPackets.Metadata.class::cast)
                .toList();
    }

    private List<FakeNametagPackets.Bundle> bundlesTo(Player viewer) {
        return packets.sends.stream()
                .filter(s -> s.viewer().getUniqueId().equals(viewer.getUniqueId()))
                .map(FakeNametagPackets.Sent::packet)
                .filter(FakeNametagPackets.Bundle.class::isInstance)
                .map(FakeNametagPackets.Bundle.class::cast)
                .toList();
    }

    private List<FakeNametagPackets.Remove> removesTo(Player viewer) {
        return packets.sends.stream()
                .filter(s -> s.viewer().getUniqueId().equals(viewer.getUniqueId()))
                .map(FakeNametagPackets.Sent::packet)
                .filter(FakeNametagPackets.Remove.class::isInstance)
                .map(FakeNametagPackets.Remove.class::cast)
                .toList();
    }

    private List<FakeNametagPackets.Metadata> metadataPacketsTo(Player viewer) {
        return packets.sends.stream()
                .filter(s -> s.viewer().getUniqueId().equals(viewer.getUniqueId()))
                .map(FakeNametagPackets.Sent::packet)
                .filter(FakeNametagPackets.Metadata.class::isInstance)
                .map(FakeNametagPackets.Metadata.class::cast)
                .toList();
    }

    private FakeNametagPackets.Metadata metadataTo(Player viewer) {
        // Pull the metadata packet out of the spawn bundle this viewer received.
        FakeNametagPackets.Bundle bundle = bundlesTo(viewer).get(0);
        return bundle.packets().stream()
                .filter(FakeNametagPackets.Metadata.class::isInstance)
                .map(FakeNametagPackets.Metadata.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private Component metadataTextTo(Player viewer) {
        return metadataTo(viewer).text();
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the constructor guard fires.
    private static NametagPackets nullPackets() {
        return null;
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the constructor guard fires.
    private static com.uxplima.uxmlib.scheduler.Scheduler nullScheduler() {
        return null;
    }
}

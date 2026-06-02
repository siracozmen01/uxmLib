package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Verifies the per-widget lifecycle SPI: a widget registers a {@link HologramLifecycle} with the manager and
 * the manager fans each player event out to it, so a widget (paged / leaderboard) resets its per-player
 * state centrally without wiring its own Bukkit listener. The fan-out is driven through the manager's
 * dispatch entry points so no live server is needed.
 */
class HologramLifecycleSpiTest {

    /** A listener that records which UUID it saw for each lifecycle hook. */
    private static final class RecordingLifecycle implements HologramLifecycle {
        private final List<UUID> joined = new ArrayList<>();
        private final List<UUID> quit = new ArrayList<>();
        private final List<UUID> worldChanged = new ArrayList<>();
        private final List<UUID> respawned = new ArrayList<>();

        @Override
        public void onJoin(UUID player) {
            joined.add(player);
        }

        @Override
        public void onQuit(UUID player) {
            quit.add(player);
        }

        @Override
        public void onWorldChange(UUID player) {
            worldChanged.add(player);
        }

        @Override
        public void onRespawn(UUID player) {
            respawned.add(player);
        }
    }

    @Test
    void dispatchFansEachHookOutToEveryRegisteredListener() {
        HologramManager manager = new HologramManager();
        RecordingLifecycle one = new RecordingLifecycle();
        RecordingLifecycle two = new RecordingLifecycle();
        manager.registerLifecycle(one);
        manager.registerLifecycle(two);
        UUID player = new UUID(0, 42);

        manager.dispatchJoin(player);
        manager.dispatchQuit(player);
        manager.dispatchWorldChange(player);
        manager.dispatchRespawn(player);

        for (RecordingLifecycle l : List.of(one, two)) {
            assertThat(l.joined).containsExactly(player);
            assertThat(l.quit).containsExactly(player);
            assertThat(l.worldChanged).containsExactly(player);
            assertThat(l.respawned).containsExactly(player);
        }
    }

    @Test
    void unregisterStopsTheFanOut() {
        HologramManager manager = new HologramManager();
        RecordingLifecycle listener = new RecordingLifecycle();
        manager.registerLifecycle(listener);
        manager.unregisterLifecycle(listener);
        UUID player = new UUID(0, 7);

        manager.dispatchQuit(player);

        assertThat(listener.quit).isEmpty();
    }

    @Test
    void quitDispatchStillInvalidatesTrackedHologramViewers() {
        // The built-in viewer-invalidation must survive the SPI generalization: a quit drops the UUID from
        // every tracked hologram's viewer set, exactly as the old listener did.
        HologramManager manager = new HologramManager();
        ForgetRecordingHologram holo = new ForgetRecordingHologram();
        manager.track(holo);
        UUID player = new UUID(0, 99);

        manager.dispatchQuit(player);

        assertThat(holo.forgotten).containsExactly(player);
    }

    @Test
    void aThrowingListenerDoesNotStopTheOthers() {
        HologramManager manager = new HologramManager();
        RecordingLifecycle good = new RecordingLifecycle();
        manager.registerLifecycle(new HologramLifecycle() {
            @Override
            public void onJoin(UUID player) {
                throw new IllegalStateException("boom");
            }
        });
        manager.registerLifecycle(good);
        UUID player = new UUID(0, 3);

        manager.dispatchJoin(player);

        assertThat(good.joined).containsExactly(player);
    }

    /** A hologram that records the UUIDs it was asked to forget; everything else is inert. */
    private static final class ForgetRecordingHologram implements Hologram {
        private final List<UUID> forgotten = new ArrayList<>();

        @Override
        public void setText(net.kyori.adventure.text.Component text) {}

        @Override
        public void moveTo(org.bukkit.Location to, int interpolationTicks) {}

        @Override
        public void setTransform(Transform transform) {}

        @Override
        public boolean attachTo(org.bukkit.entity.Entity target) {
            return false;
        }

        @Override
        public void restrictToViewers() {}

        @Override
        public void show(org.bukkit.plugin.Plugin plugin, org.bukkit.entity.Player viewer) {}

        @Override
        public void hide(org.bukkit.plugin.Plugin plugin, org.bukkit.entity.Player viewer) {}

        @Override
        public boolean isVisibleTo(org.bukkit.entity.Player viewer) {
            return false;
        }

        @Override
        public void forgetViewer(UUID viewer) {
            forgotten.add(viewer);
        }

        @Override
        public void remove() {}

        @Override
        public org.bukkit.entity.TextDisplay entity() {
            throw new UnsupportedOperationException("no entity in the fake");
        }
    }
}

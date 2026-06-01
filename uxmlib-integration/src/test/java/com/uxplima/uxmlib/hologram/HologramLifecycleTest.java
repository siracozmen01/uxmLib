package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/**
 * Verifies the per-viewer cache invalidation seam: the manager fans a viewer-forget out to every tracked
 * hologram so a quit/respawn/world-change drops that UUID from each viewer set (no leak, no stale state).
 */
class HologramLifecycleTest {

    /** A hologram that only records which viewer UUIDs it was asked to forget. */
    private static final class RecordingHologram implements Hologram {
        private final List<UUID> forgotten = new ArrayList<>();

        @Override
        public void setText(Component text) {}

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
        public TextDisplay entity() {
            throw new UnsupportedOperationException("no entity in the fake");
        }
    }

    @Test
    void invalidateViewerFansOutToEveryTrackedHologram() {
        HologramManager manager = new HologramManager();
        RecordingHologram a = new RecordingHologram();
        RecordingHologram b = new RecordingHologram();
        manager.track(a);
        manager.track(b);
        UUID viewer = new UUID(0, 7);

        manager.invalidateViewer(viewer);

        assertThat(a.forgotten).containsExactly(viewer);
        assertThat(b.forgotten).containsExactly(viewer);
    }

    @Test
    void displayHologramForgetViewerDropsFromVisibleSet() {
        // DisplayHologram tracks viewers by UUID; forgetViewer must drop the UUID so isVisibleTo goes false.
        TextDisplay display = org.mockito.Mockito.mock(TextDisplay.class);
        org.bukkit.entity.Player viewer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        org.bukkit.plugin.Plugin plugin = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class);
        UUID id = new UUID(0, 11);
        org.mockito.Mockito.when(viewer.getUniqueId()).thenReturn(id);
        DisplayHologram hologram = new DisplayHologram(display);
        hologram.show(plugin, viewer);
        assertThat(hologram.isVisibleTo(viewer)).isTrue();

        hologram.forgetViewer(id);

        assertThat(hologram.isVisibleTo(viewer)).isFalse();
    }
}

package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/** Verifies the manager despawns what it tracks — the orphan-entity guard — using a fake hologram. */
class HologramManagerTest {

    /** A Hologram with no Bukkit backing, so we can assert remove() without a live world. */
    private static final class FakeHologram implements Hologram {
        private int removed;

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
        public void forgetViewer(java.util.UUID viewer) {}

        @Override
        public void remove() {
            removed++;
        }

        @Override
        public TextDisplay entity() {
            throw new UnsupportedOperationException("no entity in the fake");
        }
    }

    @Test
    void removeAllDespawnsEveryTrackedHologram() {
        HologramManager manager = new HologramManager();
        FakeHologram a = new FakeHologram();
        FakeHologram b = new FakeHologram();
        manager.track(a);
        manager.track(b);
        assertThat(manager.count()).isEqualTo(2);

        manager.removeAll();

        assertThat(a.removed).isEqualTo(1);
        assertThat(b.removed).isEqualTo(1);
        assertThat(manager.count()).isZero();
    }

    @Test
    void removeDespawnsAndStopsTracking() {
        HologramManager manager = new HologramManager();
        FakeHologram a = new FakeHologram();
        manager.track(a);

        manager.remove(a);

        assertThat(a.removed).isEqualTo(1);
        assertThat(manager.count()).isZero();
        // A second remove is a no-op: it was already untracked.
        manager.remove(a);
        assertThat(a.removed).isEqualTo(1);
    }
}

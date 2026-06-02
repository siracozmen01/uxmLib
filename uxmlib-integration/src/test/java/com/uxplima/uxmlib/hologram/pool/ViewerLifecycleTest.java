package com.uxplima.uxmlib.hologram.pool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Drives the pure two-set lifecycle: INTENT (whom the consumer asked to show) versus VISIBILITY (who is
 * actually rendered after the gate). {@code show}/{@code hide} only touch intent; an idempotent
 * {@code reconcile} recomputes visibility from intent plus a gate predicate and emits only the transition,
 * mirroring the pool's show/hide delta. No Bukkit is needed — the gate is supplied as a plain boolean per
 * viewer.
 */
class ViewerLifecycleTest {

    /** Records the show/hide transitions the lifecycle emits so a test can assert only the delta fired. */
    private static final class RecordingRender implements ViewerLifecycle.Render {
        private final List<UUID> shown = new ArrayList<>();
        private final List<UUID> hidden = new ArrayList<>();

        @Override
        public void show(UUID viewer) {
            shown.add(viewer);
        }

        @Override
        public void hide(UUID viewer) {
            hidden.add(viewer);
        }

        void clear() {
            shown.clear();
            hidden.clear();
        }
    }

    @Test
    void reconcileShowsAnIntendedViewerThePassesTheGate() {
        UUID a = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();
        lifecycle.show(a);

        lifecycle.reconcile(a, viewer -> true, render);

        assertThat(render.shown).containsExactly(a);
        assertThat(render.hidden).isEmpty();
        assertThat(lifecycle.isVisibleTo(a)).isTrue();
        assertThat(lifecycle.isIntendedFor(a)).isTrue();
    }

    @Test
    void reconcileDoesNotShowWhenIntendedButGated() {
        UUID a = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();
        lifecycle.show(a);

        lifecycle.reconcile(a, viewer -> false, render);

        assertThat(render.shown).isEmpty();
        assertThat(lifecycle.isVisibleTo(a)).isFalse();
        // Intent survives a failed gate: the next reconcile that passes will show it.
        assertThat(lifecycle.isIntendedFor(a)).isTrue();
    }

    @Test
    void reconcileHidesAVisibleViewerThatNoLongerPassesTheGate() {
        UUID a = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();
        lifecycle.show(a);
        lifecycle.reconcile(a, viewer -> true, render);
        render.clear();

        lifecycle.reconcile(a, viewer -> false, render);

        assertThat(render.hidden).containsExactly(a);
        assertThat(render.shown).isEmpty();
        assertThat(lifecycle.isVisibleTo(a)).isFalse();
        assertThat(lifecycle.isIntendedFor(a)).isTrue();
    }

    @Test
    void reconcileIsIdempotentWhenNothingChanges() {
        UUID a = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();
        lifecycle.show(a);
        lifecycle.reconcile(a, viewer -> true, render);
        render.clear();

        lifecycle.reconcile(a, viewer -> true, render);
        lifecycle.reconcile(a, viewer -> true, render);

        assertThat(render.shown).isEmpty();
        assertThat(render.hidden).isEmpty();
        assertThat(lifecycle.isVisibleTo(a)).isTrue();
    }

    @Test
    void hideRevokesIntentAndReconcileTakesItDown() {
        UUID a = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();
        lifecycle.show(a);
        lifecycle.reconcile(a, viewer -> true, render);
        render.clear();

        lifecycle.hide(a);
        assertThat(lifecycle.isIntendedFor(a)).isFalse();
        lifecycle.reconcile(a, viewer -> true, render);

        // Intent revoked, so even a passing gate hides it.
        assertThat(render.hidden).containsExactly(a);
        assertThat(lifecycle.isVisibleTo(a)).isFalse();
    }

    @Test
    void forgetDropsBothSetsWithoutRendering() {
        UUID a = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();
        lifecycle.show(a);
        lifecycle.reconcile(a, viewer -> true, render);
        render.clear();

        lifecycle.forget(a);

        assertThat(lifecycle.isIntendedFor(a)).isFalse();
        assertThat(lifecycle.isVisibleTo(a)).isFalse();
        // forget is a quit/world-change cleanup: it never emits a packet of its own.
        assertThat(render.shown).isEmpty();
        assertThat(render.hidden).isEmpty();
    }

    @Test
    void reconcileAllVisitsEveryIntendedViewer() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();
        lifecycle.show(a);
        lifecycle.show(b);

        // a passes, b is gated out.
        lifecycle.reconcileAll(viewer -> viewer.equals(a), render);

        assertThat(render.shown).containsExactly(a);
        assertThat(lifecycle.visibleViewers()).containsExactly(a);
    }

    @Test
    void reconcileForAViewerWithNoIntentIsANoOp() {
        UUID a = UUID.randomUUID();
        RecordingRender render = new RecordingRender();
        ViewerLifecycle lifecycle = new ViewerLifecycle();

        lifecycle.reconcile(a, viewer -> true, render);

        assertThat(render.shown).isEmpty();
        assertThat(render.hidden).isEmpty();
    }
}

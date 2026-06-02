package com.uxplima.uxmlib.hologram.pool;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The two-set per-viewer visibility lifecycle from NPCLib ({@code NPCBase}, MIT pattern), applied to native
 * holograms. It separates <em>intent</em> (the viewers a consumer asked to see the hologram) from
 * <em>visibility</em> (the viewers actually rendered after a distance/world/FOV gate). {@code show}/
 * {@code hide} change only intent; an idempotent {@link #reconcile} recomputes visibility from intent plus a
 * gate predicate and emits only the show/hide that changed, so repeated reconciles — from a move event, a
 * pool tick, a respawn — cost nothing once the state has settled.
 *
 * <p>This is the per-hologram engine the {@link HologramPool} drives: the pool decides the gate (range, and
 * optionally FOV via {@link VisibilityGate}); this class owns the per-viewer two sets and the minimal delta.
 * Both sets are concurrent so reconciles from different region threads are safe.
 */
public final class ViewerLifecycle {

    private final Set<UUID> intent = ConcurrentHashMap.newKeySet();
    private final Set<UUID> visible = ConcurrentHashMap.newKeySet();

    /** Where {@link #reconcile} pushes the transitions it computes (the native show/hide, or a test sink). */
    public interface Render {

        /** Render the hologram to {@code viewer} (it was intended and now passes the gate). */
        void show(UUID viewer);

        /** Stop rendering the hologram to {@code viewer} (intent revoked or the gate now fails). */
        void hide(UUID viewer);
    }

    /** Mark {@code viewer} as intended to see the hologram. Visibility follows on the next reconcile. */
    public void show(UUID viewer) {
        intent.add(Objects.requireNonNull(viewer, "viewer"));
    }

    /** Revoke {@code viewer}'s intent. The next reconcile hides the hologram if it was rendered. */
    public void hide(UUID viewer) {
        intent.remove(Objects.requireNonNull(viewer, "viewer"));
    }

    /**
     * Drop {@code viewer} from both sets without rendering anything — the quit / world-change cleanup that
     * stops a departed UUID from leaking. A later {@link #show} plus reconcile re-establishes it cleanly.
     */
    public void forget(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        intent.remove(viewer);
        visible.remove(viewer);
    }

    /** Whether {@code viewer} is intended to see the hologram (independent of the gate). */
    public boolean isIntendedFor(UUID viewer) {
        return intent.contains(Objects.requireNonNull(viewer, "viewer"));
    }

    /** Whether the hologram is currently rendered to {@code viewer}. */
    public boolean isVisibleTo(UUID viewer) {
        return visible.contains(Objects.requireNonNull(viewer, "viewer"));
    }

    /** An immutable snapshot of the viewers currently rendered. */
    public Set<UUID> visibleViewers() {
        return Set.copyOf(visible);
    }

    /** An immutable snapshot of the viewers currently intended. */
    public Set<UUID> intendedViewers() {
        return Set.copyOf(intent);
    }

    /**
     * Recompute visibility for one {@code viewer} from intent and {@code gate} and emit only the transition.
     * Show when intended and the gate passes but not yet visible; hide when visible but no longer intended or
     * the gate fails. Idempotent: calling it again with the same inputs renders nothing.
     */
    public void reconcile(UUID viewer, Predicate<UUID> gate, Render render) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(render, "render");
        boolean wanted = intent.contains(viewer) && gate.test(viewer);
        boolean rendered = visible.contains(viewer);
        if (wanted && !rendered) {
            visible.add(viewer);
            render.show(viewer);
        } else if (!wanted && rendered) {
            visible.remove(viewer);
            render.hide(viewer);
        }
    }

    /**
     * Reconcile every intended viewer against {@code gate}. Snapshots intent first so a concurrent
     * {@link #show}/{@link #hide} cannot disturb the pass. Use this for a full re-evaluation (a pool tick or
     * a content change); use {@link #reconcile} when a single player's position changed.
     */
    public void reconcileAll(Predicate<UUID> gate, Render render) {
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(render, "render");
        for (UUID viewer : intendedViewers()) {
            reconcile(viewer, gate, render);
        }
    }
}

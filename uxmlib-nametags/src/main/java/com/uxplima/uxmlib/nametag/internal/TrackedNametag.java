package com.uxplima.uxmlib.nametag.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.LineOfSight;
import com.uxplima.uxmlib.nametag.NametagHandle;
import com.uxplima.uxmlib.nametag.NametagPackets;
import com.uxplima.uxmlib.nametag.PerViewerText;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * One target's live packet nametag: a stack of text-display entities riding the target, rendered per viewer.
 * Owns the per-line entity ids (one per stack line, allocated on demand and stable while a viewer's line count
 * holds), the spawn/metadata/mount/remove packet building, the per-viewer diff, and the line-of-sight fade.
 * Created and driven by {@code NametagRenderer}; runs on the target's region thread.
 *
 * <p>Field-ownership: every field is touched only from the target's region thread (the {@code show} call and
 * the entity-timer refresh both run there), so no synchronisation is needed.
 */
public final class TrackedNametag implements NametagHandle {

    /** Vertical gap between stacked lines, in display-translation units; the top line sits highest. */
    private static final float LINE_STEP_Y = 0.28f;

    private final NametagPackets packets;
    private final LineOfSight lineOfSight;
    private final Player target;

    /** Entity ids for the stack, index 0 = top line. Grown on demand; an id stays bound to its line index. */
    private final List<Integer> lineIds = new ArrayList<>();

    /** How many lines each current viewer is showing, so a count change can trigger a respawn for that viewer. */
    private final Map<UUID, Integer> viewerLineCounts = new HashMap<>();

    private @Nullable TaskHandle refreshTask;
    private Set<UUID> viewers;
    private PerViewerText text;
    private Appearance appearance;
    private boolean removed;

    public TrackedNametag(
            NametagPackets packets,
            LineOfSight lineOfSight,
            Player target,
            Set<UUID> viewers,
            PerViewerText text,
            Appearance appearance) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.lineOfSight = Objects.requireNonNull(lineOfSight, "lineOfSight");
        this.target = Objects.requireNonNull(target, "target");
        this.viewers = new HashSet<>(Objects.requireNonNull(viewers, "viewers"));
        this.text = Objects.requireNonNull(text, "text");
        this.appearance = Objects.requireNonNull(appearance, "appearance");
    }

    /** Send the full spawn bundle to each resolvable viewer in the initial set. */
    public void spawnAll() {
        for (UUID viewer : viewers) {
            spawnFor(viewer);
        }
    }

    /** Attach the refresh task so {@link #remove()} can cancel it. */
    public void bindRefreshTask(TaskHandle handle) {
        this.refreshTask = Objects.requireNonNull(handle, "handle");
    }

    @Override
    public void update() {
        update(viewers, text, appearance);
    }

    @Override
    public void update(Set<UUID> nextViewers, PerViewerText nextText, Appearance nextAppearance) {
        Objects.requireNonNull(nextViewers, "nextViewers");
        Objects.requireNonNull(nextText, "nextText");
        Objects.requireNonNull(nextAppearance, "nextAppearance");
        if (removed) {
            return;
        }
        Set<UUID> previous = this.viewers;
        this.text = nextText;
        this.appearance = nextAppearance;
        Set<UUID> next = new HashSet<>(nextViewers);
        for (UUID viewer : next) {
            reconcileViewer(viewer, previous.contains(viewer));
        }
        removeDepartedViewers(previous, next);
        this.viewers = next;
    }

    /**
     * A still-present viewer whose line count is unchanged gets a metadata refresh on every line (so animated
     * text, transform lerps, and the line-of-sight fade all re-apply each tick); a viewer whose count changed,
     * or a brand-new viewer, gets the full spawn bundle.
     */
    private void reconcileViewer(UUID viewer, boolean present) {
        List<Component> lines = linesFor(viewer);
        Integer shown = viewerLineCounts.get(viewer);
        if (present && shown != null && shown == lines.size()) {
            refreshLines(viewer, lines);
        } else {
            if (present && shown != null) {
                despawnFor(viewer, shown);
            }
            spawnLines(viewer, lines);
        }
    }

    /** Re-send each line's metadata to a viewer whose stack height has not changed. */
    private void refreshLines(UUID viewer, List<Component> lines) {
        int obscuredOpacity = opacityFor(viewer);
        for (int i = 0; i < lines.size(); i++) {
            sendTo(viewer, metadataPacket(lineId(i), lines.get(i), i, lines.size(), obscuredOpacity));
        }
    }

    /** Spawn the whole stack for a viewer: per-line spawn + metadata, then one mount seating every line id. */
    private void spawnLines(UUID viewer, List<Component> lines) {
        int obscuredOpacity = opacityFor(viewer);
        List<Object> frame = new ArrayList<>(lines.size() * 2 + 1);
        int[] ids = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            int id = lineId(i);
            ids[i] = id;
            frame.add(packets.spawnPacket(id, target.getX(), target.getY(), target.getZ()));
            frame.add(metadataPacket(id, lines.get(i), i, lines.size(), obscuredOpacity));
        }
        frame.add(packets.mountPacket(target.getEntityId(), ids));
        sendTo(viewer, packets.bundle(frame));
        viewerLineCounts.put(viewer, lines.size());
    }

    /** Send a remove packet to every viewer who was present last cycle but is not in {@code next}. */
    private void removeDepartedViewers(Set<UUID> previous, Set<UUID> next) {
        for (UUID viewer : previous) {
            if (!next.contains(viewer)) {
                Integer shown = viewerLineCounts.remove(viewer);
                despawnFor(viewer, shown == null ? lineIds.size() : shown);
            }
        }
    }

    @Override
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (UUID viewer : viewers) {
            Integer shown = viewerLineCounts.get(viewer);
            despawnFor(viewer, shown == null ? lineIds.size() : shown);
        }
        viewerLineCounts.clear();
        viewers = Set.of();
        if (refreshTask != null) {
            refreshTask.cancel();
        }
    }

    /** Build and send the spawn+metadata+mount bundle for a brand-new viewer. */
    private void spawnFor(UUID viewer) {
        spawnLines(viewer, linesFor(viewer));
    }

    /** Despawn the first {@code count} line entities for one viewer. */
    private void despawnFor(UUID viewer, int count) {
        if (count <= 0) {
            return;
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = lineId(i);
        }
        sendTo(viewer, packets.removePacket(ids));
    }

    /** The metadata packet for one line: its component, its stacked translation, and a per-viewer opacity. */
    private Object metadataPacket(int id, Component line, int index, int lineCount, int opacity) {
        return packets.metadataPacket(id, line, appearance, opacity, translationForLine(index, lineCount));
    }

    /** Offset a line within the stack: line 0 is the top, so earlier indices sit higher. */
    private Vector3f translationForLine(int index, int lineCount) {
        Vector3f base = appearance.translation();
        float yOffset = (lineCount - 1 - index) * LINE_STEP_Y;
        return base.add(0f, yOffset, 0f);
    }

    /** Full opacity in clear sight; the appearance's obscured opacity when the view is blocked and fade is on. */
    private int opacityFor(UUID viewer) {
        if (!appearance.hideThroughBlocks()) {
            return Appearance.FULL_OPACITY;
        }
        @Nullable Player player = target.getServer().getPlayer(viewer);
        if (player != null && lineOfSight.obstructed(player, target)) {
            return appearance.obscuredOpacity();
        }
        return Appearance.FULL_OPACITY;
    }

    /** Resolve, allocating if needed, the stable entity id bound to line {@code index}. */
    private int lineId(int index) {
        while (lineIds.size() <= index) {
            lineIds.add(packets.allocateEntityId());
        }
        return lineIds.get(index);
    }

    private List<Component> linesFor(UUID viewer) {
        List<Component> lines = text.linesFor(viewer);
        if (lines.isEmpty()) {
            throw new IllegalStateException("PerViewerText returned no lines for viewer " + viewer);
        }
        return lines;
    }

    /** Resolve the viewer to an online player and write the packet; a missing player is a silent skip. */
    private void sendTo(UUID viewer, Object packet) {
        @Nullable Player player = target.getServer().getPlayer(viewer);
        if (player != null) {
            packets.send(player, packet);
        }
    }
}

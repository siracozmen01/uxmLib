package com.uxplima.uxmlib.packet.display;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A hologram that is sent and never spawned. The viewer sees floating text at a fixed point in the world; the
 * server holds no entity for it at all.
 *
 * <h2>Why this exists beside {@code Holograms.spawn}</h2>
 *
 * <p>{@code Holograms.spawn} creates a real {@code TextDisplay}. That is the right answer when an operator
 * wants to walk up to the hologram and click it, and it stays. It is the wrong answer when the hologram is
 * decoration: a real entity is in the entity count, is written to the region file, comes back after a chunk
 * unload only if the server agrees, and can be reached by anything that reaches entities. A shop that floats
 * an item over a chest learned that the hard way, because the item was a real dropped entity and the market
 * duplicated it.
 *
 * <p>This costs the server nothing. The display exists only in the clients that were sent it, so it cannot be
 * hit, moved, duplicated, swept by an entity clearer or lost to a chunk unload.
 *
 * <h2>Distance, and going away</h2>
 *
 * <p>Every refresh the hologram asks the anchor's world who is within {@code viewDistance} blocks. A player
 * who comes into range is sent the spawn frame, a player who leaves it is sent a remove, and a player who
 * stays is sent fresh text (so a per-viewer line, a countdown or a balance stays current). When nobody is in
 * range no packet is written at all: the hologram simply is not there.
 *
 * <h2>Threading</h2>
 *
 * <p>Every frame runs through {@link Scheduler#regionTimer}, so it executes on the anchor's region thread:
 * the one thread where reading that world's players is safe. {@link #show} may be called from any thread,
 * because the first frame is scheduled onto that region rather than run inline.
 */
public final class PacketHologram {

    /** Refresh cadence when a caller names none: every half-second. */
    public static final Duration DEFAULT_REFRESH_PERIOD = Duration.ofMillis(500);

    /** How far a viewer may stand from the anchor and still be sent the hologram, when a caller names none. */
    public static final double DEFAULT_VIEW_DISTANCE = 32.0;

    /** Vertical gap between stacked lines, in display-translation units; the first line sits highest. */
    private static final float LINE_STEP_Y = 0.28f;

    private final HologramPackets packets;
    private final Location anchor;
    private final double viewDistanceSquared;

    /** Entity ids for the stack, index 0 = top line. Grown on demand; an id stays bound to its line index. */
    private final List<Integer> lineIds = new ArrayList<>();

    /** Everyone currently being shown the hologram, and how many lines each of them has. */
    private final Map<UUID, Shown> shown = new HashMap<>();

    private Function<Player, List<Component>> text;
    private HologramAppearance appearance;
    private @Nullable TaskHandle refresh;
    private boolean removed;

    private PacketHologram(
            HologramPackets packets,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<Component>> text,
            double viewDistance) {
        this.packets = packets;
        this.anchor = anchor;
        this.appearance = appearance;
        this.text = text;
        this.viewDistanceSquared = viewDistance * viewDistance;
    }

    /** Show a packet hologram at {@code anchor} with the default view distance and refresh period. */
    public static PacketHologram show(
            HologramPackets packets,
            Scheduler scheduler,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<Component>> text) {
        return show(packets, scheduler, anchor, appearance, text, DEFAULT_VIEW_DISTANCE, DEFAULT_REFRESH_PERIOD);
    }

    /**
     * Show a packet hologram at {@code anchor} and start its refresh loop.
     *
     * <p>{@code text} is asked for one viewer's lines at a time, on the anchor's region thread, on every
     * frame, so it must be cheap and free of side effects. It must return at least one line.
     *
     * @param viewDistance how far a viewer may stand from the anchor and still be sent the hologram
     * @param period how often the audience and the text are recomputed
     */
    public static PacketHologram show(
            HologramPackets packets,
            Scheduler scheduler,
            Location anchor,
            HologramAppearance appearance,
            Function<Player, List<Component>> text,
            double viewDistance,
            Duration period) {
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(anchor.getWorld(), "anchor world");
        if (viewDistance <= 0) {
            throw new IllegalArgumentException("viewDistance must be > 0, was " + viewDistance);
        }
        PacketHologram hologram = new PacketHologram(packets, anchor.clone(), appearance, text, viewDistance);
        // The first frame is scheduled rather than run inline, so show() is safe to call from any thread.
        scheduler.region(anchor, hologram::update);
        hologram.refresh = scheduler.regionTimer(anchor, period, period, handle -> hologram.update());
        return hologram;
    }

    /**
     * Recompute the audience and the text once, and reconcile every client. The refresh loop calls this; a
     * caller may call it too, on the anchor's region thread, to push a change out without waiting a period.
     */
    public void update() {
        if (removed) {
            return;
        }
        Map<UUID, Player> near = nearby();
        for (Map.Entry<UUID, Player> viewer : near.entrySet()) {
            reconcile(viewer.getKey(), viewer.getValue());
        }
        removeDeparted(near.keySet());
    }

    /** Replace the per-viewer text. It takes effect on the next {@link #update()}. */
    public void setText(Function<Player, List<Component>> next) {
        this.text = Objects.requireNonNull(next, "next");
    }

    /** Replace the look. It takes effect on the next {@link #update()}. */
    public void setAppearance(HologramAppearance next) {
        this.appearance = Objects.requireNonNull(next, "next");
    }

    /** How many players are being shown the hologram right now. */
    public int viewerCount() {
        return shown.size();
    }

    /** The point the hologram floats at. A copy, so a caller cannot move it by editing what it read. */
    public Location anchor() {
        return anchor.clone();
    }

    /** Take the hologram off every client that has it and stop the refresh loop. Safe to call twice. */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (Shown viewer : shown.values()) {
            despawn(viewer.player(), viewer.lines());
        }
        shown.clear();
        TaskHandle task = refresh;
        if (task != null) {
            task.cancel();
        }
    }

    /** The online players inside the view distance of the anchor, keyed by id. */
    private Map<UUID, Player> nearby() {
        World world = anchor.getWorld();
        if (world == null) {
            return Map.of();
        }
        Map<UUID, Player> near = new HashMap<>();
        for (Player player : world.getPlayers()) {
            @Nullable Location at = player.getLocation();
            if (at != null && at.distanceSquared(anchor) <= viewDistanceSquared) {
                near.put(player.getUniqueId(), player);
            }
        }
        return near;
    }

    /**
     * A viewer whose line count has not changed is sent fresh metadata, so animated or per-viewer text
     * re-applies every frame. A newcomer, or a viewer whose line count changed, is sent the whole spawn
     * frame: the client has to be told about an entity id before it can be painted.
     */
    private void reconcile(UUID id, Player player) {
        List<Component> lines = linesFor(player);
        Shown current = shown.get(id);
        if (current != null && current.lines() == lines.size()) {
            repaint(player, lines);
            shown.put(id, new Shown(player, lines.size()));
            return;
        }
        if (current != null) {
            despawn(player, current.lines());
        }
        spawn(player, lines);
        shown.put(id, new Shown(player, lines.size()));
    }

    /** Re-send each line's metadata to a viewer whose stack height has not changed. */
    private void repaint(Player player, List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            packets.send(player, metadata(lineId(i), lines.get(i), i, lines.size()));
        }
    }

    /** Send one viewer the whole stack: a spawn and a metadata packet per line, as a single frame. */
    private void spawn(Player player, List<Component> lines) {
        List<Object> frame = new ArrayList<>(lines.size() * 2);
        for (int i = 0; i < lines.size(); i++) {
            int id = lineId(i);
            frame.add(packets.spawnPacket(id, anchor.getX(), anchor.getY(), anchor.getZ()));
            frame.add(metadata(id, lines.get(i), i, lines.size()));
        }
        packets.send(player, packets.bundle(frame));
    }

    /** Send a remove packet to everyone who was being shown the hologram and is no longer in range. */
    private void removeDeparted(Set<UUID> stillNear) {
        for (UUID id : new HashSet<>(shown.keySet())) {
            if (!stillNear.contains(id)) {
                Shown gone = shown.remove(id);
                if (gone != null) {
                    despawn(gone.player(), gone.lines());
                }
            }
        }
    }

    /** Take the first {@code lines} line entities off one viewer's client. */
    private void despawn(Player player, int lines) {
        if (lines <= 0) {
            return;
        }
        int[] ids = new int[lines];
        for (int i = 0; i < lines; i++) {
            ids[i] = lineId(i);
        }
        packets.send(player, packets.removePacket(ids));
    }

    /** The metadata packet for one line: its component and its own offset within the stack. */
    private Object metadata(int id, Component line, int index, int lineCount) {
        return packets.metadataPacket(id, line, appearance, translationForLine(index, lineCount));
    }

    /** Offset a line within the stack: line 0 is the top, so an earlier index sits higher. */
    private Vector3f translationForLine(int index, int lineCount) {
        return appearance.translation().add(0f, (lineCount - 1 - index) * LINE_STEP_Y, 0f);
    }

    /** Resolve, allocating if needed, the stable entity id bound to line {@code index}. */
    private int lineId(int index) {
        while (lineIds.size() <= index) {
            lineIds.add(packets.allocateEntityId());
        }
        return lineIds.get(index);
    }

    private List<Component> linesFor(Player player) {
        List<Component> lines = text.apply(player);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("the hologram text returned no lines for " + player.getName());
        }
        return lines;
    }

    /** One viewer being shown the hologram: who they are, and how many lines their client is holding. */
    private record Shown(Player player, int lines) {}
}

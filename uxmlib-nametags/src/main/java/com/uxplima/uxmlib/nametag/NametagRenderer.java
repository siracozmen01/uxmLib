package com.uxplima.uxmlib.nametag;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.nametag.internal.TrackedNametag;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;

/**
 * The testable core of the packet nametag layer: it shows a stack of floating lines above a target, rendered
 * per viewer through the {@link NametagPackets} port, and keeps them fresh on a region-thread refresh task. No
 * NMS — the packets are opaque objects from the port — so this whole class runs under a fake port and a fake
 * {@link Scheduler}. Multi-line text, animated text/transform, and line-of-sight fading are all driven from
 * here: every refresh re-asks the {@link PerViewerText} for each viewer's current lines and re-sends their
 * metadata, fading a line to {@link Appearance#obscuredOpacity()} when the {@link LineOfSight} reports the
 * viewer's view is blocked and the appearance opts into hiding through blocks.
 *
 * <h2>Threading</h2>
 *
 * The refresh runs through {@link Scheduler#entityTimer}, so it executes on the target's region thread — the
 * one thread where reading the target's position and resolving online viewers is safe. Nothing here touches
 * the Bukkit API from any other thread.
 */
public final class NametagRenderer {

    /** Refresh cadence when a caller does not specify one: every half-second. */
    public static final Duration DEFAULT_REFRESH_PERIOD = Duration.ofMillis(500);

    private final NametagPackets packets;
    private final Scheduler scheduler;
    private final LineOfSight lineOfSight;

    /** Wire the renderer with the default block-based line-of-sight check. */
    public NametagRenderer(NametagPackets packets, Scheduler scheduler) {
        this(packets, scheduler, new BlockLineOfSight());
    }

    /** Wire the renderer with an explicit {@link LineOfSight}, so a test can inject a fake ray-trace. */
    public NametagRenderer(NametagPackets packets, Scheduler scheduler, LineOfSight lineOfSight) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.lineOfSight = Objects.requireNonNull(lineOfSight, "lineOfSight");
    }

    /** Show {@code target}'s nametag to {@code viewers} with the default refresh period. */
    public NametagHandle show(Player target, Appearance appearance, Set<UUID> viewers, PerViewerText text) {
        return show(target, appearance, viewers, text, DEFAULT_REFRESH_PERIOD);
    }

    /**
     * Spawn the line stack for every resolvable viewer and start a region-thread refresh loop that reconciles
     * viewers, re-resolves per-viewer text, and re-applies line-of-sight fading every {@code period}.
     *
     * @return a handle to update or remove the nametag
     */
    public NametagHandle show(
            Player target, Appearance appearance, Set<UUID> viewers, PerViewerText text, Duration period) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(viewers, "viewers");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(period, "period");
        TrackedNametag tracked = new TrackedNametag(packets, lineOfSight, target, viewers, text, appearance);
        tracked.spawnAll();
        TaskHandle refresh = scheduler.entityTimer(target, period, period, taskHandle -> tracked.update());
        tracked.bindRefreshTask(refresh);
        return tracked;
    }
}

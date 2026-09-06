package com.uxplima.uxmlib.pipeline;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.jspecify.annotations.Nullable;

/**
 * Injects and ejects a {@link PacketInterceptor} into a player's connection pipeline, and re-asserts its
 * position with a {@link PipelineWatchdog} pass when other plugins splice ahead of it.
 *
 * <p>Anchoring is by handler <em>name</em>, not index: our duplex handler is added immediately after the
 * vanilla {@code "decoder"} (so it sees fully-formed packet objects in both directions) under a stable name
 * derived from {@code handlerName}. Every pipeline mutation is idempotent: a re-inject that finds the
 * handler already present is a no-op, and an eject that finds it gone is silent, so join/quit/reorder
 * choreography can call these freely without double-add or double-remove crashes.
 *
 * <p>This class performs no scheduling itself; the inject-on-join / delayed-reorder choreography belongs to
 * the caller (via the library {@code Scheduler}), keeping this class a pure pipeline mutator that is easy to
 * reason about. It never throws on a missing channel: a player whose channel cannot be resolved (e.g. a mock
 * player under test) simply yields {@code false} from {@link #inject} / {@link #isInjected}.
 */
public class PacketPipeline {

    /** The vanilla handler our duplex handler sits immediately after; stable across 1.21.x. */
    public static final String DEFAULT_ANCHOR = "decoder";

    private final ChannelResolver resolver;
    private final PacketListenerRegistry registry;
    private final String handlerName;
    private final PipelineWatchdog watchdog;
    private final Consumer<Throwable> faultSink;

    /**
     * @param resolver reaches the player's Netty channel
     * @param registry the listeners every injected interceptor dispatches through
     * @param handlerName the unique pipeline name our handler registers under (namespace it per plugin)
     * @param faultSink receives listener throwables collected on the I/O thread, to log off-thread
     */
    public PacketPipeline(
            ChannelResolver resolver,
            PacketListenerRegistry registry,
            String handlerName,
            Consumer<Throwable> faultSink) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.handlerName = requireNonBlank(handlerName);
        this.watchdog = new PipelineWatchdog(this.handlerName, DEFAULT_ANCHOR);
        this.faultSink = Objects.requireNonNull(faultSink, "faultSink");
    }

    public String handlerName() {
        return handlerName;
    }

    /**
     * Inject our interceptor into {@code player}'s pipeline, immediately after the {@code "decoder"} anchor.
     * Idempotent. Returns {@code false} (no throw) if the channel cannot be resolved or the anchor is absent.
     */
    public boolean inject(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<Channel> channel = resolver.resolve(player);
        return channel.isPresent() && inject(channel.orElseThrow(), player.getUniqueId());
    }

    // Package-private channel seam: lets EmbeddedChannel-based tests drive the real splice without a live server.
    boolean inject(Channel channel, UUID owner) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(handlerName) != null) {
            return true; // already injected; idempotent
        }
        if (pipeline.get(DEFAULT_ANCHOR) == null) {
            return false; // unexpected pipeline shape; do not guess a position
        }
        PacketInterceptor interceptor = new PacketInterceptor(registry, () -> owner, faultSink);
        pipeline.addAfter(DEFAULT_ANCHOR, handlerName, interceptor);
        return true;
    }

    /** Remove our interceptor from {@code player}'s pipeline. Idempotent and {@link NoSuchElementException}-safe. */
    public boolean eject(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<Channel> channel = resolver.resolve(player);
        return channel.isPresent() && eject(channel.orElseThrow());
    }

    boolean eject(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(handlerName) == null) {
            return false;
        }
        try {
            pipeline.remove(handlerName);
            return true;
        } catch (NoSuchElementException raced) {
            return false; // another thread removed it between the guard and the remove; fine.
        }
    }

    /** {@code true} if our interceptor is currently present in {@code player}'s pipeline. */
    public boolean isInjected(Player player) {
        Objects.requireNonNull(player, "player");
        return resolver.resolve(player)
                .map(channel -> channel.pipeline().get(handlerName) != null)
                .orElse(false);
    }

    /**
     * Run one self-healing pass: if our handler has drifted out of position, move it back immediately after
     * the anchor. Returns the {@link PipelineWatchdog.Decision} that was applied (or merely observed). Safe to
     * call repeatedly; a no-op when nothing changed.
     */
    public PipelineWatchdog.Decision reorder(Player player) {
        Objects.requireNonNull(player, "player");
        Optional<Channel> channel = resolver.resolve(player);
        return channel.map(this::reorder).orElse(PipelineWatchdog.Decision.MISSING);
    }

    PipelineWatchdog.Decision reorder(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        List<String> names = pipeline.names();
        PipelineWatchdog.Decision decision = watchdog.evaluate(names);
        if (decision.needsReorder()) {
            applyReorder(pipeline);
        }
        return decision;
    }

    /**
     * Move our handler back to directly after the anchor. Because {@link PacketInterceptor} is intentionally
     * not {@code @Sharable}, Netty forbids re-adding any instance that has ever been added, and a non-sharable
     * handler is even poisoned by a failed add (its multiplicity check runs before the anchor lookup throws).
     * So every add attempt uses a brand-new {@link PacketInterceptor#freshCopy() copy}.
     *
     * <p>The move is non-atomic (remove then re-add), so if the anchor vanishes in the gap: another plugin
     * mutating the pipeline concurrently, the re-add at the anchor fails. We must never leave the handler
     * dropped: on that failure we splice a fresh copy at the tail so interception stays active rather than
     * silently disabled.
     */
    private void applyReorder(ChannelPipeline pipeline) {
        @Nullable ChannelHandler handler = pipeline.get(handlerName);
        if (!(handler instanceof PacketInterceptor interceptor)) {
            return;
        }
        pipeline.remove(handlerName);
        try {
            reanchor(pipeline, interceptor.freshCopy());
        } catch (NoSuchElementException anchorGone) {
            restoreAfterFailedReorder(pipeline, interceptor);
        }
    }

    /**
     * Splice a copy back in immediately after the anchor. Package-private (not private) so a test can
     * intercept the exact remove-then-re-add window and simulate a concurrent plugin deleting the anchor; in
     * production it is a single {@code addAfter}.
     */
    void reanchor(ChannelPipeline pipeline, PacketInterceptor handler) {
        pipeline.addAfter(DEFAULT_ANCHOR, handlerName, handler);
    }

    /** The anchor disappeared between remove and re-add; put a fresh copy back so our handler is never dropped. */
    private void restoreAfterFailedReorder(ChannelPipeline pipeline, PacketInterceptor source) {
        if (pipeline.get(handlerName) != null) {
            return; // a concurrent inject already re-added it; nothing to restore.
        }
        try {
            pipeline.addAfter(DEFAULT_ANCHOR, handlerName, source.freshCopy());
        } catch (NoSuchElementException stillGone) {
            pipeline.addLast(handlerName, source.freshCopy());
        }
    }

    private static String requireNonBlank(@Nullable String value) {
        Objects.requireNonNull(value, "handlerName");
        if (value.isBlank()) {
            throw new IllegalArgumentException("handlerName must not be blank");
        }
        return value;
    }
}

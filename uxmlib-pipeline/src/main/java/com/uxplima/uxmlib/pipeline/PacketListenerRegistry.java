package com.uxplima.uxmlib.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

/**
 * An ordered, thread-safe set of {@link PacketListener}s and the dispatch that folds their verdicts into one.
 *
 * <p>Registration order is preserved (listeners run first-registered-first). Backed by a
 * {@link CopyOnWriteArrayList} so dispatch from the Netty I/O thread never blocks registration from the main
 * thread and a snapshot is iterated lock-free. Dispatch is <b>fail-open</b>: a listener that throws is
 * treated as a {@link PacketAction#PASS} and the throwable is collected so the caller can log it off the I/O
 * thread, never swallowed silently in a way that loses the cause.
 *
 * <p>The folding rule is a veto: the packet is cancelled if <em>any</em> listener returns
 * {@link PacketAction#CANCEL}. Every listener still sees the packet even after one cancels, so listeners that
 * only observe stay correct. A {@link PacketVerdict#rewrite(Object) rewrite} folds in below cancel: if no
 * listener cancels and one returns a rewrite, the folded result carries that replacement (the
 * <em>first</em> rewrite in registration order wins; later listeners still see the original packet, never the
 * pending replacement, so the dispatch stays order-independent and observe-only listeners stay correct). A
 * cancel always beats a rewrite, so a cancelling listener is never overridden by a rewriting one.
 */
public final class PacketListenerRegistry {

    private final CopyOnWriteArrayList<PacketListener> listeners = new CopyOnWriteArrayList<>();

    /** Register {@code listener} at the end of the dispatch order. A duplicate instance is not added twice. */
    public void register(PacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.addIfAbsent(listener);
    }

    /** Remove {@code listener}; returns {@code true} if it was present. */
    public boolean unregister(PacketListener listener) {
        Objects.requireNonNull(listener, "listener");
        return listeners.remove(listener);
    }

    /** {@code true} if no listener is registered (the interceptor can then forward without dispatching). */
    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    /** An immutable snapshot of the current listeners in dispatch order. */
    public List<PacketListener> snapshot() {
        return List.copyOf(listeners);
    }

    /**
     * Dispatch {@code packet} (travelling {@code direction} for the connection owned by {@code player}) to
     * every listener and fold the verdicts. Never throws: a listener fault is folded as {@code PASS} and
     * attached to the result.
     */
    public Dispatch dispatch(PacketDirection direction, @Nullable UUID player, Object packet) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(packet, "packet");
        boolean cancel = false;
        @Nullable Object replacement = null;
        @Nullable List<Throwable> faults = null;
        for (PacketListener listener : listeners) {
            try {
                PacketVerdict verdict = invoke(listener, direction, player, packet);
                if (verdict.cancels()) {
                    cancel = true;
                } else if (replacement == null) {
                    // First rewrite in registration order wins; a later listener still sees the original packet,
                    // never the pending replacement, so this fold stays order-independent for observers.
                    replacement = verdict.replacement();
                }
            } catch (RuntimeException fault) {
                faults = record(faults, fault);
            }
        }
        PacketAction action = cancel ? PacketAction.CANCEL : PacketAction.PASS;
        return new Dispatch(action, cancel ? null : replacement, faults == null ? List.of() : faults);
    }

    private static PacketVerdict invoke(
            PacketListener listener, PacketDirection direction, @Nullable UUID player, Object packet) {
        return direction == PacketDirection.OUTBOUND
                ? listener.onSendVerdict(player, packet)
                : listener.onReceiveVerdict(player, packet);
    }

    private static List<Throwable> record(@Nullable List<Throwable> faults, Throwable fault) {
        List<Throwable> sink = faults == null ? new java.util.ArrayList<>() : faults;
        sink.add(fault);
        return sink;
    }

    /**
     * The folded outcome of a dispatch: the verdict, the optional replacement packet a rewrite folded in (never
     * set when the packet was cancelled), and any listener faults collected along the way (so the caller logs
     * them off the I/O thread).
     */
    public record Dispatch(PacketAction action, @Nullable Object replacement, List<Throwable> faults) {

        public Dispatch {
            Objects.requireNonNull(action, "action");
            faults = List.copyOf(faults);
        }

        /** A dispatch that only passes or cancels, carrying no replacement: the back-compat shape. */
        public Dispatch(PacketAction action, List<Throwable> faults) {
            this(action, null, faults);
        }

        /** {@code true} if the folded verdict cancels the packet. */
        public boolean cancelled() {
            return action.cancels();
        }

        /** {@code true} if the dispatch folded in a replacement packet to forward in place of the original. */
        public boolean rewritten() {
            return replacement != null;
        }
    }
}

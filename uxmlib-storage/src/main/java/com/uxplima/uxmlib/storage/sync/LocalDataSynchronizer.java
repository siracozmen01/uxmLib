package com.uxplima.uxmlib.storage.sync;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The single-node {@link DataSynchronizer}: an in-process pub/sub bus. A {@link #publish} delivers the
 * message synchronously to every handler currently subscribed on that channel, on the calling thread, and
 * to no handler on any other channel. This is the default a plugin runs with until it opts into a networked
 * transport ({@link RedisDataSynchronizer}); the same code path keeps working, it simply stays local.
 *
 * <p>Per-channel handler lists are {@link CopyOnWriteArrayList}s so a publish iterates a stable snapshot
 * even if a handler subscribes or unsubscribes mid-delivery, and a handler that throws is logged-around
 * (its failure never aborts delivery to the rest). Thread-safe; closing is idempotent and final.
 */
public final class LocalDataSynchronizer implements DataSynchronizer {

    private final Map<String, CopyOnWriteArrayList<Consumer<String>>> channels = new ConcurrentHashMap<>();
    private volatile boolean closed;

    @Override
    public void publish(String channel, String message) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");
        if (closed) {
            return;
        }
        List<Consumer<String>> handlers = channels.get(channel);
        if (handlers == null) {
            return;
        }
        for (Consumer<String> handler : handlers) {
            deliver(handler, message);
        }
    }

    @Override
    public Subscription subscribe(String channel, Consumer<String> handler) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        if (closed) {
            throw new IllegalStateException("synchronizer is closed");
        }
        channels.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> remove(channel, handler);
    }

    @Override
    public void close() {
        closed = true;
        channels.clear();
    }

    /** The number of channels that currently have at least one subscriber — for tests and diagnostics. */
    public int channelCount() {
        return channels.size();
    }

    private void remove(String channel, Consumer<String> handler) {
        channels.computeIfPresent(channel, (key, handlers) -> {
            handlers.remove(handler);
            return handlers.isEmpty() ? null : handlers;
        });
    }

    private static void deliver(Consumer<String> handler, String message) {
        try {
            handler.accept(message);
        } catch (RuntimeException ignored) {
            // One handler must never block delivery to the others; a synchronizer has no logger of its own,
            // so a misbehaving subscriber is contained here and the fan-out continues.
        }
    }
}

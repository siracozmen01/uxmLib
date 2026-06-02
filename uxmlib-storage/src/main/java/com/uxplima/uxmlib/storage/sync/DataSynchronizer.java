package com.uxplima.uxmlib.storage.sync;

import java.util.function.Consumer;

/**
 * A publish/subscribe SPI for cross-server coordination — chiefly cache invalidation ("user 42 changed,
 * drop your copy") and small fan-out events between server nodes that share a backend.
 *
 * <p>A message is a plain {@link String}; the caller chooses the encoding (an id, a small JSON blob, a
 * {@link SyncMessage} frame). Channels are opaque names. The single-node default is
 * {@link LocalDataSynchronizer} (an in-process bus); {@link RedisDataSynchronizer} spans nodes over Redis
 * pub/sub while keeping the same contract.
 *
 * <p>Implementations are expected to be thread-safe. Delivery to a handler that throws must not abort
 * delivery to the other handlers on that channel. After {@link #close()} the instance rejects further use.
 */
public interface DataSynchronizer extends AutoCloseable {

    /**
     * Broadcast {@code message} to every subscriber currently listening on {@code channel}. With a
     * networked implementation this reaches subscribers on other nodes too; the publishing node may or may
     * not receive its own message depending on the transport (callers should not rely on a local echo).
     *
     * @throws NullPointerException if either argument is {@code null}
     */
    void publish(String channel, String message);

    /**
     * Register {@code handler} to receive messages published on {@code channel}. Returns a
     * {@link Subscription} whose {@link Subscription#unsubscribe()} removes exactly this handler.
     *
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalStateException if this synchronizer is already closed
     */
    Subscription subscribe(String channel, Consumer<String> handler);

    /** Release every subscription and any underlying transport. Idempotent. */
    @Override
    void close();
}

package com.uxplima.uxmlib.redis;

import java.util.function.Consumer;

/**
 * A low-level binary Redis pub/sub channel: publish a raw {@code byte[]} frame to a named channel and
 * subscribe to a named channel. The transport seam a higher-level typed message bus sits on — it owns only
 * the wire (Lettuce PUBLISH / SUBSCRIBE), leaving encoding, routing and echo-suppression to the caller.
 *
 * <p>This is the binary counterpart of the higher-level, {@code String}-payload cross-server synchronizer:
 * use it when the payload is an opaque binary frame (a codec's output) rather than a small text id/blob.
 *
 * <p>Threading: {@link #publish} may be called from any thread and must not block the caller
 * (fire-and-forget over the client's event loop). {@link #subscribe} delivers frames on the client's pub/sub
 * thread; a caller that needs another thread bridges there itself. Redis pub/sub delivers a publisher its own
 * messages, so a caller that publishes and subscribes on the same node must de-duplicate its own echo.
 */
public interface RedisBus extends AutoCloseable {

    /** Publish a single pre-encoded frame to {@code channel}. Fire-and-forget; never blocks the caller. */
    void publish(String channel, byte[] frame);

    /** Subscribe to {@code channel}; {@code onFrame} receives each raw payload published there. */
    void subscribe(String channel, Consumer<byte[]> onFrame);

    /** Release the underlying connection(s). Idempotent. */
    @Override
    void close();
}

package com.uxplima.uxmlib.storage.sync;

import java.util.Objects;
import java.util.function.Consumer;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * A {@link DataSynchronizer} backed by Redis pub/sub, so a {@link #publish} fans out across every server
 * node connected to the same Redis. This is the opt-in networked transport; construct it only when Redis is
 * actually configured (Lettuce is a {@code compileOnly} soft-dependency — referencing this class without
 * {@code io.lettuce:lettuce-core} on the runtime classpath will not link, which is the intended present-guard).
 *
 * <p>All logical channels are multiplexed over one physical Redis channel ({@code umbrella}). Each message is
 * framed by {@link SyncMessage} (channel name carried inline) so the receiving node can demultiplex with no
 * shared table and no per-channel Redis subscribe/unsubscribe traffic. Local fan-out — subscriber lists,
 * per-handler unsubscribe, swallowing a throwing handler — is delegated to an embedded
 * {@link LocalDataSynchronizer}; this class only adds the network hop. Thread-safe; {@link #close()} tears
 * down the connection and client and is idempotent.
 *
 * <p>This adapter is exercised against a real Redis in integration, not in the unit suite (MockBukkit and the
 * JUnit run have no broker). The wire format it depends on — {@link SyncMessage} — is covered purely.
 */
public final class RedisDataSynchronizer implements DataSynchronizer {

    /** The default physical channel every node subscribes to; logical channels ride inside the frame. */
    public static final String DEFAULT_UMBRELLA_CHANNEL = "uxmlib:sync";

    private final RedisClient client;
    private final StatefulRedisPubSubConnection<String, String> connection;
    private final LocalDataSynchronizer local = new LocalDataSynchronizer();
    private final String umbrella;
    private volatile boolean closed;

    /**
     * Connect to Redis at {@code uri} and subscribe this node to {@link #DEFAULT_UMBRELLA_CHANNEL}.
     *
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public RedisDataSynchronizer(String uri) {
        this(uri, DEFAULT_UMBRELLA_CHANNEL);
    }

    /**
     * Connect to Redis at {@code uri} and subscribe this node to {@code umbrella}. All nodes that must see
     * each other's messages have to share the same umbrella channel.
     *
     * @throws NullPointerException if either argument is {@code null}
     */
    public RedisDataSynchronizer(String uri, String umbrella) {
        Objects.requireNonNull(uri, "uri");
        this.umbrella = Objects.requireNonNull(umbrella, "umbrella");
        this.client = RedisClient.create(RedisURI.create(uri));
        this.connection = client.connectPubSub();
        this.connection.addListener(new Receiver());
        this.connection.sync().subscribe(umbrella);
    }

    // Publish is fire-and-forget: a cache-invalidation broadcast is best-effort, so the PUBLISH future is
    // intentionally not awaited (awaiting it would block the caller on a network round-trip).
    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void publish(String channel, String message) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");
        if (closed) {
            return;
        }
        String frame = SyncMessage.encode(new SyncMessage(channel, message));
        connection.async().publish(umbrella, frame);
    }

    @Override
    public Subscription subscribe(String channel, Consumer<String> handler) {
        if (closed) {
            throw new IllegalStateException("synchronizer is closed");
        }
        return local.subscribe(channel, handler);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        local.close();
        connection.close();
        client.shutdown();
    }

    /** Demultiplexes an inbound umbrella frame and re-publishes it onto the embedded local bus. */
    private final class Receiver extends RedisPubSubAdapter<String, String> {
        @Override
        public void message(String channel, String frame) {
            if (closed || !umbrella.equals(channel)) {
                return;
            }
            SyncMessage decoded = SyncMessage.decode(frame);
            local.publish(decoded.channel(), decoded.payload());
        }
    }
}

package com.uxplima.uxmlib.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test for {@link LettuceRedisBus} against a real Redis: a publishing bus PUBLISHes a binary frame
 * and a second subscribing bus receives the exact bytes. Resolves the broker from
 * {@code UXMLIB_TEST_REDIS_URI} (or {@code redis://localhost:6379}) and skips cleanly — never fails — when no
 * Redis is reachable, so {@code ./gradlew build} stays green on hosts without one.
 */
@org.jspecify.annotations.NullUnmarked
class RedisBusIntegrationTest {

    private static @Nullable String redisUri;

    @BeforeAll
    static void resolveRedis() {
        String env = System.getenv("UXMLIB_TEST_REDIS_URI");
        String uri = env != null && !env.isBlank() ? env : "redis://localhost:6379";
        Assumptions.assumeTrue(reachable(uri), "no Redis reachable at " + uri + " — skipping");
        redisUri = uri;
    }

    @Test
    void published_frame_reaches_a_subscriber_byte_for_byte() throws InterruptedException {
        String channel = "uxmlib:test:" + UUID.randomUUID();
        RedisURI uri = RedisURI.create(redisUri);
        LettuceRedisBus publisher = new LettuceRedisBus(RedisClient.create(uri), message -> {});
        LettuceRedisBus subscriber = new LettuceRedisBus(RedisClient.create(uri), message -> {});

        BlockingQueue<byte[]> received = new ArrayBlockingQueue<>(4);
        subscriber.subscribe(channel, received::add);

        // High bytes (200, 255) and an embedded NUL prove the wire is binary-safe (a String codec would
        // mangle these); arbitrary codec output must survive untouched.
        byte[] frame = {1, 2, 3, (byte) 200, 0, (byte) 255, 42};

        try {
            publisher.publish(channel, frame);
            byte[] got = received.poll(10, TimeUnit.SECONDS);
            assertThat(got).isEqualTo(frame);
        } finally {
            publisher.close();
            subscriber.close();
        }
    }

    private static boolean reachable(String uri) {
        try {
            RedisURI parsed = RedisURI.create(uri);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(parsed.getHost(), parsed.getPort()), 1500);
                return true;
            }
        } catch (Exception unreachable) {
            return false;
        }
    }
}

package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * The {@link Cooldowns} service is the dependency-free per-key gate behind {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Cooldown}. Driven by a fake clock so the arm ->
 * still-on-cooldown -> expired progression is deterministic without sleeping.
 */
class CooldownsTest {

    @Test
    void firstCheckArmsAndReportsNoRemaining() {
        AtomicLong now = new AtomicLong(1_000L);
        Cooldowns cooldowns = new Cooldowns(now::get);

        assertThat(cooldowns.check("k", 5_000L)).isZero();
    }

    @Test
    void aSecondCheckWithinTheWindowReportsTheRemainingMillis() {
        AtomicLong now = new AtomicLong(1_000L);
        Cooldowns cooldowns = new Cooldowns(now::get);

        cooldowns.check("k", 5_000L); // arms until 6_000
        now.set(2_500L);

        assertThat(cooldowns.check("k", 5_000L)).isEqualTo(3_500L);
    }

    @Test
    void afterTheWindowElapsesItRearmsAndReportsNoRemaining() {
        AtomicLong now = new AtomicLong(1_000L);
        Cooldowns cooldowns = new Cooldowns(now::get);

        cooldowns.check("k", 5_000L); // arms until 6_000
        now.set(6_000L); // exactly at expiry: no longer on cooldown

        assertThat(cooldowns.check("k", 5_000L)).isZero();
        // The expired check re-armed, so an immediate re-check is back on cooldown.
        assertThat(cooldowns.check("k", 5_000L)).isEqualTo(5_000L);
    }

    @Test
    void distinctKeysDoNotInterfere() {
        AtomicLong now = new AtomicLong(0L);
        Cooldowns cooldowns = new Cooldowns(now::get);

        cooldowns.check("a", 1_000L);

        assertThat(cooldowns.check("b", 1_000L)).isZero();
    }

    @Test
    void aNonPositiveDurationNeverArms() {
        AtomicLong now = new AtomicLong(0L);
        Cooldowns cooldowns = new Cooldowns(now::get);

        assertThat(cooldowns.check("k", 0L)).isZero();
        assertThat(cooldowns.check("k", -5L)).isZero();
        // Nothing was stored, so the map stays empty (lazy: no spurious entries).
        assertThat(cooldowns.size()).isZero();
    }

    @Test
    void expiredEntriesAreEvictedLazilyOnCheck() {
        AtomicLong now = new AtomicLong(0L);
        Cooldowns cooldowns = new Cooldowns(now::get);

        cooldowns.check("k", 1_000L);
        assertThat(cooldowns.size()).isEqualTo(1);

        now.set(2_000L);
        cooldowns.check("k", 1_000L); // expired -> evicted then re-armed
        assertThat(cooldowns.size()).isEqualTo(1);
    }

    @Test
    void anExistingWindowEndingExactlyAtTheNewExpiryStillGates() {
        // Boundary the atomic check-and-arm must get right: an existing window whose expiry equals what a fresh
        // arm at this instant would produce must still report "on cooldown", never mistake itself for a re-arm.
        AtomicLong now = new AtomicLong(1_000L);
        Cooldowns cooldowns = new Cooldowns(now::get);
        cooldowns.check("k", 1_000L); // arms until 2_000
        // now stays at 1_000: a fresh arm would also target 2_000 (1_000 + 1_000), colliding with the live window.

        assertThat(cooldowns.check("k", 1_000L)).isEqualTo(1_000L);
    }

    @Test
    void manyConcurrentChecksOnAnElapsedKeyRearmExactlyOnce() throws InterruptedException {
        // The thread-safety contract under contention: with every thread seeing the key as elapsed at the same
        // instant, the atomic check-and-arm must let exactly one win. A non-atomic get/put would let several
        // each believe they re-armed. Best-effort stress guard (the win is timing-dependent), run over rounds.
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            for (int round = 0; round < 50; round++) {
                assertSingleRearmUnderContention(pool);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertSingleRearmUnderContention(ExecutorService pool) throws InterruptedException {
        AtomicLong now = new AtomicLong(1_000L);
        Cooldowns cooldowns = new Cooldowns(now::get);
        cooldowns.check("k", 1_000L); // arms until 2_000
        now.set(2_000L); // at expiry: the window has elapsed for everyone racing below

        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger rearmed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                awaitQuietly(start);
                if (cooldowns.check("k", 1_000L) == 0L) {
                    rearmed.incrementAndGet();
                }
                done.countDown();
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        // Exactly one thread won the elapsed window; the rest saw it freshly re-armed and were gated.
        assertThat(rearmed).hasValue(1);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting for the start gate", interrupted);
        }
    }

    @Test
    void defaultClockUsesWallTime() {
        Cooldowns cooldowns = new Cooldowns();

        assertThat(cooldowns.check("k", 60_000L)).isZero();
        assertThat(cooldowns.check("k", 60_000L)).isPositive();
    }
}

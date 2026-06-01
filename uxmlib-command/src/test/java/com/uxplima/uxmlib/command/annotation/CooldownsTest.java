package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

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
    void defaultClockUsesWallTime() {
        Cooldowns cooldowns = new Cooldowns();

        assertThat(cooldowns.check("k", 60_000L)).isZero();
        assertThat(cooldowns.check("k", 60_000L)).isPositive();
    }
}

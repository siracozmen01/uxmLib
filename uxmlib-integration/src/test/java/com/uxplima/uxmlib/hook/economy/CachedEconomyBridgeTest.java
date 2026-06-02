package com.uxplima.uxmlib.hook.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TTL caching and write-invalidation of {@link CachedEconomyBridge}, driven by a hand-advanced clock. */
class CachedEconomyBridgeTest {

    private MutableClock clock;
    private CountingBridge underlying;
    private CachedEconomyBridge cached;
    private OfflinePlayer player;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        underlying = new CountingBridge();
        cached = new CachedEconomyBridge(underlying, Duration.ofSeconds(5), clock);
        player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void aSecondReadInsideTheTtlServesTheCachedValueWithoutHittingTheProvider() {
        assertThat(cached.balance(player)).isEqualTo(100.0d);
        assertThat(cached.balance(player)).isEqualTo(100.0d);

        assertThat(underlying.balanceCalls).isEqualTo(1);
    }

    @Test
    void aReadAfterTheTtlExpiresRefreshesFromTheProvider() {
        cached.balance(player);
        clock.advance(Duration.ofSeconds(6));

        cached.balance(player);

        assertThat(underlying.balanceCalls).isEqualTo(2);
    }

    @Test
    void aReadExactlyAtTheTtlBoundaryIsStillTreatedAsExpired() {
        cached.balance(player);
        clock.advance(Duration.ofSeconds(5));

        cached.balance(player);

        assertThat(underlying.balanceCalls).isEqualTo(2);
    }

    @Test
    void withdrawInvalidatesTheCacheSoTheNextReadRefreshes() {
        cached.balance(player);
        cached.withdraw(player, 10.0d);

        cached.balance(player);

        assertThat(underlying.balanceCalls).isEqualTo(2);
    }

    @Test
    void depositInvalidatesTheCacheSoTheNextReadRefreshes() {
        cached.balance(player);
        cached.deposit(player, 10.0d);

        cached.balance(player);

        assertThat(underlying.balanceCalls).isEqualTo(2);
    }

    @Test
    void explicitInvalidateForcesARefresh() {
        cached.balance(player);
        cached.invalidate(player);

        cached.balance(player);

        assertThat(underlying.balanceCalls).isEqualTo(2);
    }

    @Test
    void invalidateAllClearsEveryEntry() {
        cached.balance(player);
        cached.invalidateAll();

        cached.balance(player);

        assertThat(underlying.balanceCalls).isEqualTo(2);
    }

    @Test
    void nonBalanceReadsAndCurrencyMetadataPassThroughUncached() {
        cached.has(player, 5.0d);
        cached.has(player, 5.0d);

        assertThat(underlying.hasCalls).isEqualTo(2);
        assertThat(cached.isPresent()).isTrue();
        assertThat(cached.format(1.0d)).isEqualTo("$1.00");
        assertThat(cached.currencySymbol()).isEqualTo("Dollar");
        assertThat(cached.currencyNameSingular()).isEqualTo("Dollar");
        assertThat(cached.currencyNamePlural()).isEqualTo("Dollars");
    }

    /** A bridge that counts provider hits so a test can prove the cache absorbs repeated reads. */
    private static final class CountingBridge implements EconomyBridge {
        int balanceCalls;
        int hasCalls;

        @Override
        public double balance(OfflinePlayer player) {
            balanceCalls++;
            return 100.0d;
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            hasCalls++;
            return true;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public String format(double amount) {
            return "$1.00";
        }

        @Override
        public String currencySymbol() {
            return "Dollar";
        }

        @Override
        public String currencyNameSingular() {
            return "Dollar";
        }

        @Override
        public String currencyNamePlural() {
            return "Dollars";
        }
    }

    /** A {@link Clock} a test moves forward by hand to drive TTL expiry deterministically. */
    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            this.now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}

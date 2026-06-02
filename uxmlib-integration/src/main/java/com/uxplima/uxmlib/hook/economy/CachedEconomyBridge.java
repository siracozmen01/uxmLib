package com.uxplima.uxmlib.hook.economy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.OfflinePlayer;

/**
 * An {@link EconomyBridge} decorator that caches {@link #balance(OfflinePlayer)} for a short TTL, so a
 * caller redrawing balances often (a GUI refreshing per tick, a leaderboard) does not hammer the backing
 * provider, which may block. The TTL is measured against an injected {@link Clock} so tests can advance
 * time deterministically. A {@link #withdraw}/{@link #deposit} moves money, so it invalidates the cached
 * entry before delegating — the next read goes back to the provider. All other reads pass straight
 * through. The decorator owns no scheduling: it refreshes lazily on the read that finds a stale entry.
 */
public final class CachedEconomyBridge implements EconomyBridge {

    private final EconomyBridge delegate;
    private final Clock clock;
    private final Duration ttl;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    /** Wrap {@code delegate}, caching balances for {@code ttl} as measured by {@code clock}. */
    public CachedEconomyBridge(EconomyBridge delegate, Duration ttl, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive: " + ttl);
        }
    }

    @Override
    public double balance(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        Instant now = clock.instant();
        Entry cached = cache.get(player.getUniqueId());
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }
        double fresh = delegate.balance(player);
        cache.put(player.getUniqueId(), new Entry(fresh, now.plus(ttl)));
        return fresh;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return delegate.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        invalidate(player);
        return delegate.withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        invalidate(player);
        return delegate.deposit(player, amount);
    }

    /** Drop {@code player}'s cached balance so the next read refreshes from the provider. */
    public void invalidate(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        cache.remove(player.getUniqueId());
    }

    /** Drop every cached balance. */
    public void invalidateAll() {
        cache.clear();
    }

    @Override
    public boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public String format(double amount) {
        return delegate.format(amount);
    }

    @Override
    public String currencySymbol() {
        return delegate.currencySymbol();
    }

    @Override
    public String currencyNameSingular() {
        return delegate.currencyNameSingular();
    }

    @Override
    public String currencyNamePlural() {
        return delegate.currencyNamePlural();
    }

    private record Entry(double value, Instant expiresAt) {}
}

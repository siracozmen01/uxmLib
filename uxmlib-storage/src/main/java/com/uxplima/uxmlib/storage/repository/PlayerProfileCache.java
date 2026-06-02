package com.uxplima.uxmlib.storage.repository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Ticker;
import org.jspecify.annotations.Nullable;

/**
 * An optional two-tier read cache tuned for player profiles: while a player is online their profile is
 * <em>pinned</em> — held by a strong reference that is never read through again nor evicted — and when they
 * quit it is <em>demoted</em> into a Caffeine TTL tier that drops it once an idle window passes. This is the
 * "permanent-while-online, TTL-after-quit" policy a server wants so an active player never re-hits the
 * database while a player who left does not linger in memory forever.
 *
 * <p>It composes the existing pieces rather than replacing them: the online tier is a small concurrent map
 * and the offline tier is the project's {@link Cache} ({@code expireAfterAccess} as the quit TTL). Reach for
 * {@link CachedStorage} instead when one flat write-through map is enough; this is the player-lifecycle
 * variant. Online/offline transitions are driven explicitly by the consumer from join/quit events, so the
 * cache has no dependency on the scheduler or the server.
 */
public final class PlayerProfileCache<I, T> {

    private final StorageProvider<I, T> backend;
    private final Function<T, I> idOf;
    private final ConcurrentHashMap<I, T> online = new ConcurrentHashMap<>();
    private final Cache<I, T> offline;

    private PlayerProfileCache(StorageProvider<I, T> backend, Function<T, I> idOf, Cache<I, T> offline) {
        this.backend = backend;
        this.idOf = idOf;
        this.offline = offline;
    }

    /** Start configuring a cache. */
    public static <I, T> Builder<I, T> builder() {
        return new Builder<>();
    }

    /**
     * Mark {@code id}'s player online: load the profile (from a demoted entry, else the backend) and pin it
     * for the duration of the session. Returns the profile if one exists. Call this on join.
     */
    public Optional<T> markOnline(I id) {
        Objects.requireNonNull(id, "id");
        T pinned = online.get(id);
        if (pinned != null) {
            return Optional.of(pinned);
        }
        Optional<T> loaded = readThrough(id);
        loaded.ifPresent(value -> {
            online.put(id, value);
            offline.invalidate(id);
        });
        return loaded;
    }

    /**
     * Mark {@code id}'s player offline: unpin the profile and demote it into the TTL tier, where it stays
     * readable until the quit window elapses. A no-op if the id was not pinned. Call this on quit.
     */
    public void markOffline(I id) {
        Objects.requireNonNull(id, "id");
        T pinned = online.remove(id);
        if (pinned != null) {
            offline.put(id, pinned);
        }
    }

    /**
     * The profile for {@code id}: the pinned copy if online, else the demoted copy if still inside the quit
     * TTL, else read through the backend and cache the result in the TTL tier.
     */
    public Optional<T> get(I id) {
        Objects.requireNonNull(id, "id");
        T pinned = online.get(id);
        if (pinned != null) {
            return Optional.of(pinned);
        }
        Optional<T> demoted = offline.getIfPresent(id);
        if (demoted.isPresent()) {
            return demoted;
        }
        Optional<T> loaded = readThrough(id);
        loaded.ifPresent(value -> offline.put(id, value));
        return loaded;
    }

    /**
     * Update the cached copy of {@code entity} in whichever tier currently holds it (the pin if its player is
     * online, otherwise the TTL tier). Does <strong>not</strong> write to the backend — persist through the
     * backend separately; this only keeps the in-memory view fresh.
     */
    public void put(T entity) {
        Objects.requireNonNull(entity, "entity");
        I id = idOf.apply(entity);
        if (online.containsKey(id)) {
            online.put(id, entity);
        } else {
            offline.put(id, entity);
        }
    }

    /** Whether {@code id}'s player is currently pinned as online. */
    public boolean isOnline(I id) {
        return online.containsKey(Objects.requireNonNull(id, "id"));
    }

    /** How many profiles are pinned online. */
    public int onlineCount() {
        return online.size();
    }

    /** Run pending TTL maintenance on the offline tier so eviction is observable immediately (tests). */
    public void cleanUp() {
        offline.cleanUp();
    }

    private Optional<T> readThrough(I id) {
        return backend.findById(id);
    }

    /** Fluent builder for a {@link PlayerProfileCache}. */
    public static final class Builder<I, T> {
        // Long enough that a brief disconnect/rejoin is served from memory, short enough not to hoard.
        private Duration ttlAfterQuit = Duration.ofMinutes(5);
        private long maximumOffline = 10_000L;
        private @Nullable Ticker ticker;

        private Builder() {}

        /** How long a profile lingers in the TTL tier after its player quits before being evicted. */
        public Builder<I, T> ttlAfterQuit(Duration duration) {
            Objects.requireNonNull(duration, "duration");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("ttlAfterQuit must be positive");
            }
            this.ttlAfterQuit = duration;
            return this;
        }

        /** The maximum number of demoted (offline) profiles to keep before size eviction kicks in. */
        public Builder<I, T> maximumOffline(long size) {
            if (size < 1) {
                throw new IllegalArgumentException("maximumOffline must be >= 1");
            }
            this.maximumOffline = size;
            return this;
        }

        /** The time source for the quit TTL; supply a fake one to make eviction deterministic in a test. */
        public Builder<I, T> ticker(Ticker ticker) {
            this.ticker = Objects.requireNonNull(ticker, "ticker");
            return this;
        }

        /** Build the cache over {@code backend}, given how to read an entity's id. */
        public PlayerProfileCache<I, T> build(StorageProvider<I, T> backend, Function<T, I> idOf) {
            Objects.requireNonNull(backend, "backend");
            Objects.requireNonNull(idOf, "idOf");
            Cache.Builder offlineBuilder =
                    Cache.builder().maximumSize(maximumOffline).expireAfterAccess(ttlAfterQuit);
            Ticker clock = ticker;
            if (clock != null) {
                offlineBuilder = offlineBuilder.ticker(clock);
            }
            return new PlayerProfileCache<>(backend, idOf, offlineBuilder.build());
        }
    }
}

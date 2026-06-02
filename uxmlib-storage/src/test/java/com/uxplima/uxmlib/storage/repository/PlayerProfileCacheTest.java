package com.uxplima.uxmlib.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Covers the two-tier online/offline policy: a profile is pinned (held permanently) while its player is
 * online, then demoted to a TTL tier on quit and dropped once that window passes. A fake ticker drives the
 * TTL so the eviction is deterministic.
 */
class PlayerProfileCacheTest {

    record Profile(String id, int level) {}

    /** A counting in-memory backend so the test can see exactly when it is read through. */
    static final class FakeBackend implements StorageProvider<String, Profile> {
        private final Map<String, Profile> store = new LinkedHashMap<>();
        int reads;

        @Override
        public Optional<Profile> findById(String id) {
            reads++;
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Profile> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void save(Profile entity) {
            store.put(entity.id(), entity);
        }

        @Override
        public boolean deleteById(String id) {
            return store.remove(id) != null;
        }
    }

    @Test
    void onlineProfileIsPinnedAndNeverReReadOrEvicted() {
        FakeBackend backend = new FakeBackend();
        backend.save(new Profile("p1", 3));
        AtomicLong nanos = new AtomicLong();
        PlayerProfileCache<String, Profile> cache = PlayerProfileCache.<String, Profile>builder()
                .ttlAfterQuit(Duration.ofSeconds(10))
                .ticker(nanos::get)
                .build(backend, Profile::id);

        cache.markOnline("p1");
        backend.reads = 0;

        nanos.set(Duration.ofHours(1).toNanos()); // long past any TTL
        assertThat(cache.get("p1")).get().extracting(Profile::level).isEqualTo(3);
        assertThat(backend.reads).isZero(); // pinned: served without a backend hit
        assertThat(cache.isOnline("p1")).isTrue();
    }

    @Test
    void quittingDemotesToTheTtlTierThenEvictsAfterTheWindow() {
        FakeBackend backend = new FakeBackend();
        backend.save(new Profile("p1", 5));
        AtomicLong nanos = new AtomicLong();
        PlayerProfileCache<String, Profile> cache = PlayerProfileCache.<String, Profile>builder()
                .ttlAfterQuit(Duration.ofSeconds(10))
                .ticker(nanos::get)
                .build(backend, Profile::id);

        cache.markOnline("p1");
        cache.markOffline("p1");
        assertThat(cache.isOnline("p1")).isFalse();

        // Still inside the TTL window: a re-join is served from the demoted tier, no backend read.
        backend.reads = 0;
        nanos.set(Duration.ofSeconds(5).toNanos());
        assertThat(cache.get("p1")).isPresent();
        assertThat(backend.reads).isZero();

        // Past the window: evicted, so the next read goes back to the backend.
        nanos.set(Duration.ofSeconds(30).toNanos());
        cache.cleanUp();
        backend.reads = 0;
        assertThat(cache.get("p1")).isPresent();
        assertThat(backend.reads).isEqualTo(1);
    }

    @Test
    void getReadsThroughForAnUnknownIdAndCachesInTheTtlTier() {
        FakeBackend backend = new FakeBackend();
        backend.save(new Profile("p1", 1));
        PlayerProfileCache<String, Profile> cache =
                PlayerProfileCache.<String, Profile>builder().build(backend, Profile::id);

        assertThat(cache.get("p1")).isPresent();
        assertThat(cache.get("p1")).isPresent(); // second read served from the TTL tier
        assertThat(backend.reads).isEqualTo(1);
        assertThat(cache.isOnline("p1")).isFalse();
    }

    @Test
    void markOnlineLoadsTheProfileAndReturnsIt() {
        FakeBackend backend = new FakeBackend();
        backend.save(new Profile("p1", 8));
        PlayerProfileCache<String, Profile> cache =
                PlayerProfileCache.<String, Profile>builder().build(backend, Profile::id);

        assertThat(cache.markOnline("p1")).get().extracting(Profile::level).isEqualTo(8);
        assertThat(cache.onlineCount()).isEqualTo(1);
    }

    @Test
    void putUpdatesThePinForAnOnlineProfile() {
        FakeBackend backend = new FakeBackend();
        backend.save(new Profile("p1", 1));
        PlayerProfileCache<String, Profile> cache =
                PlayerProfileCache.<String, Profile>builder().build(backend, Profile::id);
        cache.markOnline("p1");

        cache.put(new Profile("p1", 2));

        backend.reads = 0;
        assertThat(cache.get("p1")).get().extracting(Profile::level).isEqualTo(2);
        assertThat(backend.reads).isZero(); // still pinned, updated in place
    }

    @Test
    void rejectsANonPositiveTtl() {
        assertThatThrownBy(() -> PlayerProfileCache.builder().ttlAfterQuit(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void rejectsANullBackend() {
        assertThatThrownBy(() -> PlayerProfileCache.builder().build(null, Object::toString))
                .isInstanceOf(NullPointerException.class);
    }
}

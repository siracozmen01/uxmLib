package com.uxplima.uxmlib.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Covers the write-through cache / data-holder semantics over an in-memory backend. */
class CachedStorageTest {

    record User(String id, int score) {}

    /** A counting in-memory StorageProvider so the test can see when the backend is actually hit. */
    static final class FakeBackend implements StorageProvider<String, User> {
        private final Map<String, User> store = new LinkedHashMap<>();
        int reads;
        int writes;

        @Override
        public Optional<User> findById(String id) {
            reads++;
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void save(User entity) {
            writes++;
            store.put(entity.id(), entity);
        }

        @Override
        public boolean deleteById(String id) {
            return store.remove(id) != null;
        }
    }

    @Test
    void readsThroughOnceThenServesFromCache() {
        FakeBackend backend = new FakeBackend();
        backend.save(new User("u1", 5));
        CachedStorage<String, User> storage = new CachedStorage<>(backend, User::id);
        backend.reads = 0;

        assertThat(storage.get("u1")).get().extracting(User::score).isEqualTo(5);
        assertThat(storage.get("u1")).isPresent(); // second get served from cache
        assertThat(backend.reads).isEqualTo(1); // backend hit only once
    }

    @Test
    void saveWritesThroughAndUpdatesCache() {
        FakeBackend backend = new FakeBackend();
        CachedStorage<String, User> storage = new CachedStorage<>(backend, User::id);

        storage.save(new User("u1", 10));
        assertThat(backend.writes).isEqualTo(1);
        assertThat(storage.cached("u1")).get().extracting(User::score).isEqualTo(10);
    }

    @Test
    void saveAndInvalidateFlushesThenDrops() {
        FakeBackend backend = new FakeBackend();
        CachedStorage<String, User> storage = new CachedStorage<>(backend, User::id);
        storage.load("u1"); // miss, nothing cached
        storage.save(new User("u1", 7));

        storage.saveAndInvalidate("u1"); // save-on-quit
        assertThat(storage.cached("u1")).isEmpty();
        assertThat(backend.findById("u1")).get().extracting(User::score).isEqualTo(7);
    }

    @Test
    void saveAllFlushesEveryLoadedEntity() {
        FakeBackend backend = new FakeBackend();
        CachedStorage<String, User> storage = new CachedStorage<>(backend, User::id);
        storage.save(new User("a", 1));
        storage.save(new User("b", 2));
        backend.writes = 0;

        storage.saveAll();
        assertThat(backend.writes).isEqualTo(2);
        assertThat(storage.loadedCount()).isEqualTo(2);
    }
}

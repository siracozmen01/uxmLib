package com.uxplima.uxmlib.storage;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * A write-through cache over a {@link StorageProvider}: reads populate an in-memory map on a miss, writes
 * go to the backend and then update the cache, so hot data (an online player's row) stays in memory while
 * the database remains the source of truth. Pin entries while they're active (a player joins) and drop
 * them when done (a player quits) with {@link #load}/{@link #invalidate}; {@link #saveAll} flushes
 * everything that's loaded. The keyed map is concurrent so loads and saves from different threads are
 * safe.
 */
public final class CachedStorage<I, T> {

    private final StorageProvider<I, T> backend;
    private final Function<T, I> idOf;
    private final ConcurrentHashMap<I, T> cache = new ConcurrentHashMap<>();

    public CachedStorage(StorageProvider<I, T> backend, Function<T, I> idOf) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.idOf = Objects.requireNonNull(idOf, "idOf");
    }

    /** The cached value for {@code id}, reading through to the backend and caching it on a miss. */
    public Optional<T> get(I id) {
        Objects.requireNonNull(id, "id");
        T cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<T> loaded = backend.findById(id);
        loaded.ifPresent(value -> cache.put(id, value));
        return loaded;
    }

    /** Load {@code id} into the cache (e.g. on join) and return it if present. */
    public Optional<T> load(I id) {
        return get(id);
    }

    /** The currently cached value for {@code id} without touching the backend. */
    public Optional<T> cached(I id) {
        return Optional.ofNullable(cache.get(Objects.requireNonNull(id, "id")));
    }

    /** Write {@code entity} to the backend, then update the cache. */
    public void save(T entity) {
        Objects.requireNonNull(entity, "entity");
        backend.save(entity);
        cache.put(idOf.apply(entity), entity);
    }

    /** Write {@code entity} off-thread on {@code executor}, updating the cache when it completes. */
    public CompletableFuture<Void> saveAsync(Executor executor, T entity) {
        return Async.on(executor, () -> {
            save(entity);
            return null;
        });
    }

    /** Flush every loaded entity to the backend (e.g. on a periodic autosave). */
    public void saveAll() {
        for (T entity : cache.values()) {
            backend.save(entity);
        }
    }

    /** Drop {@code id} from the cache (e.g. on quit) without deleting it from the backend. */
    public void invalidate(I id) {
        cache.remove(Objects.requireNonNull(id, "id"));
    }

    /** Save {@code id} if loaded, then drop it from the cache — the save-on-quit step. */
    public void saveAndInvalidate(I id) {
        Objects.requireNonNull(id, "id");
        T entity = cache.remove(id);
        if (entity != null) {
            backend.save(entity);
        }
    }

    /** How many entities are currently held in memory. */
    public int loadedCount() {
        return cache.size();
    }
}

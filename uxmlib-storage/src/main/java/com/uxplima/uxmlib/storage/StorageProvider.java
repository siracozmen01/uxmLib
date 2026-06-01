package com.uxplima.uxmlib.storage;

import java.util.List;
import java.util.Optional;

/**
 * One backend for storing aggregates of type {@code T} keyed by {@code I}, so code that loads and saves
 * data depends on this and not on whether the data lives in SQL or in flat files. A {@link Repository} is
 * the SQL implementation (adapt it with {@link #of(Repository, java.util.function.Function)}); a flat-file
 * implementation writes one file per aggregate. The consumer picks the backend at wiring time.
 */
public interface StorageProvider<I, T> {

    /** The aggregate with id {@code id}, or empty. */
    Optional<T> findById(I id);

    /** Every stored aggregate. */
    List<T> findAll();

    /** Insert or replace {@code entity}. */
    void save(T entity);

    /** Delete the aggregate with id {@code id}; returns whether one was removed. */
    boolean deleteById(I id);

    /** Adapt a {@link Repository} (the SQL backend) to this SPI, given how to read an entity's id. */
    static <I, T> StorageProvider<I, T> of(Repository<I, T> repository, java.util.function.Function<T, I> idOf) {
        java.util.Objects.requireNonNull(repository, "repository");
        java.util.Objects.requireNonNull(idOf, "idOf");
        return new StorageProvider<>() {
            @Override
            public Optional<T> findById(I id) {
                return repository.findById(id);
            }

            @Override
            public List<T> findAll() {
                return repository.findAll();
            }

            @Override
            public void save(T entity) {
                repository.save(entity);
            }

            @Override
            public boolean deleteById(I id) {
                return repository.deleteById(id);
            }
        };
    }
}

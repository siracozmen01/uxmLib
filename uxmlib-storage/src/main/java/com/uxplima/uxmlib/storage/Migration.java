package com.uxplima.uxmlib.storage;

import java.util.Objects;

/**
 * One schema migration: a monotonically increasing {@code version}, a human {@code description}, and the
 * {@code sql} to apply (one or more statements separated by {@code ;}). {@link MigrationRunner} applies
 * pending migrations in version order and records which have run, so each runs exactly once.
 */
public record Migration(int version, String description, String sql) {

    public Migration {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(sql, "sql");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }
}

package com.uxplima.uxmlib.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.uxplima.uxmlib.storage.StorageException;

/**
 * Small helpers for prepared-statement work over a {@link Database}, so callers do not re-derive the
 * borrow-connection / prepare / bind / iterate / close-everything dance. Every method uses
 * try-with-resources and prepared statements — never string concatenation — and borrows a fresh pooled
 * connection per call. Checked {@link SQLException}s are wrapped in {@link StorageException}.
 */
public final class Sql {

    private static final System.Logger LOG = System.getLogger(Sql.class.getName());

    private final Database database;

    public Sql(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** The SQL dialect of the underlying database, for dialect-specific statements such as upsert. */
    public Dialect dialect() {
        return database.dialect();
    }

    /** Run a query and map every row, in result-set order. */
    public <T> List<T> query(String sql, StatementBinder binder, RowMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        Objects.requireNonNull(mapper, "mapper");
        try (Connection conn = database.connection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rows.next()) {
                    results.add(mapper.map(rows));
                }
                return List.copyOf(results);
            }
        } catch (SQLException failure) {
            throw new StorageException("query failed: " + sql, failure);
        }
    }

    /** Run a {@link SelectBuilder}-built {@link Query} and map every row. */
    public <T> List<T> query(Query query, RowMapper<T> mapper) {
        Objects.requireNonNull(query, "query");
        return query(query.sql(), query.binder(), mapper);
    }

    /** Run a query and map the first row, if any. */
    public <T> Optional<T> queryFirst(String sql, StatementBinder binder, RowMapper<T> mapper) {
        List<T> results = query(sql, binder, mapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Run a query and map every row, but tolerate a malformed row: when {@code mapper} throws on a single row
     * (a renamed key, a value that no longer parses), that row is logged and skipped instead of aborting the
     * whole load. Returns the rows that mapped cleanly plus how many were skipped, so one corrupt record never
     * kills an entire load. A failure of the query itself (the connection, the statement, advancing the cursor)
     * still aborts as a {@link StorageException} — only a per-row mapper exception is recoverable.
     */
    public <T> LoadResult<T> queryResilient(String sql, StatementBinder binder, RowMapper<T> mapper) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        Objects.requireNonNull(mapper, "mapper");
        try (Connection conn = database.connection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return mapResilient(rows, mapper, sql);
            }
        } catch (SQLException failure) {
            throw new StorageException("resilient query failed: " + sql, failure);
        }
    }

    private static <T> LoadResult<T> mapResilient(ResultSet rows, RowMapper<T> mapper, String sql) throws SQLException {
        List<T> results = new ArrayList<>();
        int skipped = 0;
        while (rows.next()) {
            try {
                results.add(mapper.map(rows));
            } catch (RuntimeException badRow) {
                skipped++;
                LOG.log(System.Logger.Level.WARNING, "skipped a malformed row during load: " + sql, badRow);
            }
        }
        return new LoadResult<>(results, skipped);
    }

    /** Run a query off-thread on {@code executor}; the future completes with the mapped rows. */
    public <T> CompletableFuture<List<T>> queryAsync(
            Executor executor, String sql, StatementBinder binder, RowMapper<T> mapper) {
        return Async.on(executor, () -> query(sql, binder, mapper));
    }

    /** Run a write off-thread on {@code executor}; the future completes with the affected row count. */
    public CompletableFuture<Integer> updateAsync(Executor executor, String sql, StatementBinder binder) {
        return Async.on(executor, () -> update(sql, binder));
    }

    /**
     * Run {@code sql} once per binder as a single JDBC batch, returning the per-row update counts. The
     * whole batch runs in one transaction so it is all-or-nothing.
     */
    public int[] batch(String sql, List<StatementBinder> binders) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binders, "binders");
        try (Connection conn = database.connection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                for (StatementBinder binder : binders) {
                    binder.bind(statement);
                    statement.addBatch();
                }
                int[] counts = statement.executeBatch();
                conn.commit();
                return counts;
            } catch (SQLException failure) {
                conn.rollback();
                throw failure;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new StorageException("batch failed: " + sql, failure);
        }
    }

    /** Run an INSERT/UPDATE/DELETE (or DDL) and return the affected row count. */
    public int update(String sql, StatementBinder binder) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        try (Connection conn = database.connection();
                PreparedStatement statement = conn.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new StorageException("update failed: " + sql, failure);
        }
    }

    /** Run a builder-built write ({@link UpdateBuilder} / {@link DeleteBuilder}) and return the affected row count. */
    public int update(Query query) {
        Objects.requireNonNull(query, "query");
        return update(query.sql(), query.binder());
    }

    /**
     * Run an INSERT and return the auto-generated key it produced (typically an auto-increment id), via
     * {@link java.sql.Statement#RETURN_GENERATED_KEYS}. Works across SQLite, H2, MySQL/MariaDB and Postgres
     * — the portable JDBC path that avoids a dialect-specific {@code RETURNING} clause in the caller's SQL.
     *
     * @throws IllegalStateException if the statement runs but the driver reports no generated key
     * @throws StorageException if the insert fails
     */
    public long insertReturningKey(String sql, StatementBinder binder) {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(binder, "binder");
        try (Connection conn = database.connection();
                PreparedStatement statement = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(statement);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("insert produced no generated key: " + sql);
                }
                return keys.getLong(1);
            }
        } catch (SQLException failure) {
            throw new StorageException("insert failed: " + sql, failure);
        }
    }

    /** Run an {@link InsertBuilder}-built insert and return the auto-generated key it produced. */
    public long insertReturningKey(Query query) {
        Objects.requireNonNull(query, "query");
        return insertReturningKey(query.sql(), query.binder());
    }

    /** Run a parameterless statement (typically DDL). */
    public void execute(String sql) {
        update(sql, StatementBinder.NONE);
    }

    /**
     * Stream a whole table to {@code consumer} in key order using keyset (seek) pagination: each page is
     * fetched with {@code WHERE keyColumn > ? ORDER BY keyColumn LIMIT pageSize}, the cursor advancing to the
     * last key of the page rather than an {@code OFFSET}. A page is buffered and its connection returned to
     * the pool before the consumer runs, so a multi-thousand-row table is processed without loading it all and
     * without holding a connection across the callback (the callback may itself query the database).
     *
     * <p><strong>{@code keyColumn} must be strictly unique.</strong> The cursor advances with {@code key > ?},
     * so any rows that share the last key of a page (a duplicate sitting on the page boundary) are skipped
     * silently — exactly-once holds only for a unique key. Pass a primary/unique key; this is not validated.
     *
     * @param table the table to walk; a simple SQL identifier
     * @param keyColumn a <em>strictly unique</em>, monotonically comparable column to seek on (typically the
     *     primary key); a non-unique column silently drops rows that tie on a page boundary
     * @param pageSize rows per page; must be {@code >= 1}
     * @param mapper maps each row to the value handed to {@code consumer}
     * @param consumer receives every row exactly once, in {@code keyColumn} order (given a unique key)
     */
    public <T> void forEachByKey(
            String table, String keyColumn, int pageSize, RowMapper<T> mapper, Consumer<? super T> consumer) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(keyColumn, "keyColumn");
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(consumer, "consumer");
        new KeysetPager(database).forEach(table, keyColumn, pageSize, mapper, consumer);
    }
}

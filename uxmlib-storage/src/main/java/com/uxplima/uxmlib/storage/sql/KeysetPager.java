package com.uxplima.uxmlib.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmlib.storage.StorageException;
import org.jspecify.annotations.Nullable;

/**
 * Keyset (seek) pagination over a key-ordered table: walk it in {@code pageSize} chunks with
 * {@code WHERE key > ? ORDER BY key LIMIT n}, advancing the cursor to the last key of each page instead of
 * an {@code OFFSET} that re-scans the prefix. Each page is read into a buffer and the borrowed connection is
 * returned to the pool <em>before</em> the consumer runs, so a long callback never pins a connection and the
 * whole table can be processed without loading it into memory at once.
 *
 * <p>The key column and table are validated as identifiers (DDL/identifier positions cannot be bound) while
 * the cursor value is always a bound {@code ?} parameter, so the walk is injection-safe by construction.
 */
final class KeysetPager {

    private final Database database;

    KeysetPager(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** A page of mapped rows plus the seek key of its last row (null for an empty page). */
    private record Page<T>(List<T> rows, @Nullable Object lastKey) {}

    <T> void forEach(String table, String keyColumn, int pageSize, RowMapper<T> mapper, Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        @Nullable Object cursor = null;
        while (true) {
            Page<T> page = fetchPage(table, keyColumn, pageSize, mapper, cursor);
            if (page.rows().isEmpty()) {
                return;
            }
            // The connection is closed by now: run the (possibly slow, possibly re-querying) callback freely.
            for (T row : page.rows()) {
                consumer.accept(row);
            }
            if (page.rows().size() < pageSize) {
                return;
            }
            cursor = page.lastKey();
        }
    }

    private <T> Page<T> fetchPage(
            String table, String keyColumn, int pageSize, RowMapper<T> mapper, @Nullable Object cursor) {
        Query query = pageQuery(table, keyColumn, pageSize, cursor);
        try (Connection conn = database.connection();
                PreparedStatement statement = conn.prepareStatement(query.sql())) {
            query.binder().bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return readPage(rows, keyColumn, mapper);
            }
        } catch (SQLException failure) {
            throw new StorageException("keyset page failed: " + query.sql(), failure);
        }
    }

    private static <T> Page<T> readPage(ResultSet rows, String keyColumn, RowMapper<T> mapper) throws SQLException {
        List<T> mapped = new ArrayList<>();
        Object lastKey = null;
        while (rows.next()) {
            mapped.add(mapper.map(rows));
            lastKey = rows.getObject(keyColumn);
        }
        return new Page<>(mapped, lastKey);
    }

    private static Query pageQuery(String table, String keyColumn, int pageSize, @Nullable Object cursor) {
        SelectBuilder select = SelectBuilder.from(table);
        if (cursor != null) {
            select.where(keyColumn, ">", cursor);
        } else {
            // Validate keyColumn even on the first page (no WHERE) so an injecting name fails fast.
            select.orderBy(keyColumn);
            return select.limit(pageSize).build();
        }
        return select.orderBy(keyColumn).limit(pageSize).build();
    }
}

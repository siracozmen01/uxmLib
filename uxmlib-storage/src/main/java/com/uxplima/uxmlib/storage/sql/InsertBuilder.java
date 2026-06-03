package com.uxplima.uxmlib.storage.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A small, safe INSERT builder. Each value goes through a bound {@code ?} placeholder and every column is
 * validated against the same strict allowlist as {@link SelectBuilder}, so the produced {@link Query} is
 * injection-safe by construction. Deliberately minimal — a single-row insert into one table. For a
 * multi-row insert, an {@code INSERT ... SELECT}, or a NULL/default column value, write the statement by
 * hand and use {@link Sql#update(String, StatementBinder)}.
 *
 * <pre>{@code
 * Query q = InsertBuilder.into("players")
 *     .set("name", "Steve")
 *     .set("coins", 100)
 *     .build();
 * long id = new Sql(database).insertReturningKey(q);
 * }</pre>
 */
public final class InsertBuilder {

    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final List<Object> values = new ArrayList<>();

    private InsertBuilder(String table) {
        this.table = SqlIdentifiers.identifier(table, "table");
    }

    /** Start an {@code INSERT INTO table}. */
    public static InsertBuilder into(String table) {
        return new InsertBuilder(table);
    }

    /** Set {@code column} to a bound {@code value}. Setting the same column twice is rejected. */
    public InsertBuilder set(String column, Object value) {
        String name = SqlIdentifiers.identifier(column, "column");
        Objects.requireNonNull(value, "value");
        if (columns.contains(name)) {
            throw new IllegalArgumentException("column '" + name + "' is set twice");
        }
        columns.add(name);
        values.add(value);
        return this;
    }

    /** Render the {@link Query}; throws if no column was set. */
    public Query build() {
        if (columns.isEmpty()) {
            throw new IllegalStateException("insert needs at least one value");
        }
        String placeholders = Stream.generate(() -> "?").limit(columns.size()).collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
        return new Query(sql, values);
    }
}

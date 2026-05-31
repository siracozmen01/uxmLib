package com.uxplima.uxmlib.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A small, safe SELECT builder. Column and table names are caller-controlled identifiers written into
 * the SQL; every <em>value</em> goes through a bound {@code ?} placeholder, so the produced {@link Query}
 * is injection-safe by construction. Deliberately minimal — for joins or vendor-specific SQL, write the
 * statement directly and use {@link Sql#query(String, StatementBinder, RowMapper)}.
 *
 * <pre>{@code
 * Query q = SelectBuilder.from("players")
 *     .columns("name", "coins")
 *     .where("world", "world_nether")
 *     .orderByDescending("coins")
 *     .limit(10)
 *     .build();
 * }</pre>
 */
public final class SelectBuilder {

    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final List<String> conditions = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();
    private @org.jspecify.annotations.Nullable String orderBy;
    private boolean descending;
    private int limit = -1;

    private SelectBuilder(String table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    /** Start a SELECT from {@code table}. */
    public static SelectBuilder from(String table) {
        return new SelectBuilder(table);
    }

    /** The columns to select; selects {@code *} when none are given. */
    public SelectBuilder columns(String... names) {
        Objects.requireNonNull(names, "names");
        for (String name : names) {
            columns.add(Objects.requireNonNull(name, "column"));
        }
        return this;
    }

    /** An equality condition {@code column = ?} bound to {@code value}. Conditions are AND-combined. */
    public SelectBuilder where(String column, Object value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        conditions.add(column + " = ?");
        parameters.add(value);
        return this;
    }

    /** A comparison condition {@code column <op> ?} bound to {@code value} (op e.g. {@code ">="}). */
    public SelectBuilder where(String column, String operator, Object value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(value, "value");
        conditions.add(column + " " + operator + " ?");
        parameters.add(value);
        return this;
    }

    /** Order ascending by {@code column}. */
    public SelectBuilder orderBy(String column) {
        this.orderBy = Objects.requireNonNull(column, "column");
        this.descending = false;
        return this;
    }

    /** Order descending by {@code column}. */
    public SelectBuilder orderByDescending(String column) {
        this.orderBy = Objects.requireNonNull(column, "column");
        this.descending = true;
        return this;
    }

    /** Limit the result to {@code max} rows. */
    public SelectBuilder limit(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        this.limit = max;
        return this;
    }

    /** Render the {@link Query}. */
    public Query build() {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(columns.isEmpty() ? "*" : String.join(", ", columns));
        sql.append(" FROM ").append(table);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (orderBy != null) {
            sql.append(" ORDER BY ").append(orderBy).append(descending ? " DESC" : " ASC");
        }
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return new Query(sql.toString(), parameters);
    }
}

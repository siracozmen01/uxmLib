package com.uxplima.uxmlib.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A small, safe SELECT builder. Every <em>value</em> goes through a bound {@code ?} placeholder, and the
 * column/table identifiers and comparison operators it writes into the SQL are validated against a strict
 * allowlist, so the produced {@link Query} is injection-safe by construction even if a caller threads
 * untrusted input into a column name. Deliberately minimal — for joins or vendor-specific SQL, write the
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

    // A SQL identifier we are willing to inline: a bare name or dotted/quoted-free table.column, nothing
    // that could carry an injection. Anything else must go through a hand-written statement instead.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    // The only comparison operators the builder will inline; everything else is rejected.
    private static final Set<String> OPERATORS = Set.of("=", "!=", "<>", "<", "<=", ">", ">=", "LIKE", "IS", "IS NOT");

    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final List<String> conditions = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();
    private @org.jspecify.annotations.Nullable String orderBy;
    private boolean descending;
    private int limit = -1;

    private SelectBuilder(String table) {
        this.table = identifier(table, "table");
    }

    /** Start a SELECT from {@code table}. */
    public static SelectBuilder from(String table) {
        return new SelectBuilder(table);
    }

    /** The columns to select; selects {@code *} when none are given. */
    public SelectBuilder columns(String... names) {
        Objects.requireNonNull(names, "names");
        for (String name : names) {
            columns.add(identifier(name, "column"));
        }
        return this;
    }

    /** An equality condition {@code column = ?} bound to {@code value}. Conditions are AND-combined. */
    public SelectBuilder where(String column, Object value) {
        Objects.requireNonNull(value, "value");
        conditions.add(identifier(column, "column") + " = ?");
        parameters.add(value);
        return this;
    }

    /** A comparison condition {@code column <op> ?} bound to {@code value} (op e.g. {@code ">="}). */
    public SelectBuilder where(String column, String operator, Object value) {
        Objects.requireNonNull(value, "value");
        conditions.add(identifier(column, "column") + " " + operator(operator) + " ?");
        parameters.add(value);
        return this;
    }

    /** Order ascending by {@code column}. */
    public SelectBuilder orderBy(String column) {
        this.orderBy = identifier(column, "column");
        this.descending = false;
        return this;
    }

    /** Order descending by {@code column}. */
    public SelectBuilder orderByDescending(String column) {
        this.orderBy = identifier(column, "column");
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

    private static String identifier(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a simple SQL identifier (got '" + value
                    + "'); write the statement by hand for anything else");
        }
        return value;
    }

    private static String operator(String value) {
        Objects.requireNonNull(value, "operator");
        String normalised = value.strip().toUpperCase(java.util.Locale.ROOT);
        if (!OPERATORS.contains(normalised)) {
            throw new IllegalArgumentException("unsupported operator '" + value + "'; allowed: " + OPERATORS);
        }
        return normalised;
    }
}

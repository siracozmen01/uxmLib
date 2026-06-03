package com.uxplima.uxmlib.storage.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A small, safe UPDATE builder. Each assignment and condition value goes through a bound {@code ?}
 * placeholder and every column is validated against the same strict allowlist as {@link SelectBuilder}, so
 * the produced {@link Query} is injection-safe by construction.
 *
 * <p><strong>An update with no {@link #where} touches every row.</strong> That is legal SQL and sometimes
 * intended, but add a condition unless you mean to.
 *
 * <pre>{@code
 * Query q = UpdateBuilder.table("players")
 *     .set("coins", 100)
 *     .where("id", 5)
 *     .build();
 * int rows = new Sql(database).update(q);
 * }</pre>
 */
public final class UpdateBuilder {

    private final String table;
    private final List<String> assignments = new ArrayList<>();
    private final List<Object> values = new ArrayList<>();
    private final WhereClause where = new WhereClause();

    private UpdateBuilder(String table) {
        this.table = SqlIdentifiers.identifier(table, "table");
    }

    /** Start an {@code UPDATE table}. */
    public static UpdateBuilder table(String table) {
        return new UpdateBuilder(table);
    }

    /** Assign {@code column = ?} to a bound {@code value}. */
    public UpdateBuilder set(String column, Object value) {
        Objects.requireNonNull(value, "value");
        assignments.add(SqlIdentifiers.identifier(column, "column") + " = ?");
        values.add(value);
        return this;
    }

    /** Add an equality condition {@code column = ?}. Conditions are AND-combined. */
    public UpdateBuilder where(String column, Object value) {
        where.eq(column, value);
        return this;
    }

    /** Add a comparison condition {@code column <op> ?} (op e.g. {@code ">="}). */
    public UpdateBuilder where(String column, String operator, Object value) {
        where.compare(column, operator, value);
        return this;
    }

    /** Render the {@link Query}; throws if no assignment was set. */
    public Query build() {
        if (assignments.isEmpty()) {
            throw new IllegalStateException("update needs at least one assignment");
        }
        String sql = "UPDATE " + table + " SET " + String.join(", ", assignments) + where.render();
        List<Object> params = new ArrayList<>(values);
        params.addAll(where.parameters());
        return new Query(sql, params);
    }
}

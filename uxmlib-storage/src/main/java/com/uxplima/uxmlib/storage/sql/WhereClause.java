package com.uxplima.uxmlib.storage.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Accumulates AND-combined WHERE conditions shared by {@link UpdateBuilder} and {@link DeleteBuilder}. Each
 * value is bound through a {@code ?} placeholder; column names and operators go through
 * {@link SqlIdentifiers}, so the rendered fragment is injection-safe by construction.
 */
final class WhereClause {

    private final List<String> conditions = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();

    /** Add an equality condition {@code column = ?} bound to {@code value}. */
    void eq(String column, Object value) {
        Objects.requireNonNull(value, "value");
        conditions.add(SqlIdentifiers.identifier(column, "column") + " = ?");
        parameters.add(value);
    }

    /** Add a comparison condition {@code column <op> ?} bound to {@code value}. */
    void compare(String column, String operator, Object value) {
        Objects.requireNonNull(value, "value");
        conditions.add(SqlIdentifiers.identifier(column, "column") + " " + SqlIdentifiers.operator(operator) + " ?");
        parameters.add(value);
    }

    /** Render {@code " WHERE a = ? AND b > ?"}, or {@code ""} when no condition was added. */
    String render() {
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /** The bound parameters, in render order. */
    List<Object> parameters() {
        return parameters;
    }
}

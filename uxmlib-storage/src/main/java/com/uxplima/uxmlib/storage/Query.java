package com.uxplima.uxmlib.storage;

import java.util.List;
import java.util.Objects;

/**
 * A built SQL statement: the parameterised {@code sql} text and the ordered {@code parameters} that fill
 * its {@code ?} placeholders. Produced by {@link SelectBuilder}; pass it to {@link Sql#query(Query,
 * RowMapper)} so the binding is handled for you and no value is ever concatenated into the SQL.
 */
public record Query(String sql, List<Object> parameters) {

    public Query {
        Objects.requireNonNull(sql, "sql");
        Objects.requireNonNull(parameters, "parameters");
        parameters = List.copyOf(parameters);
    }

    /** Bind this query's parameters onto a statement, in order (1-based). */
    StatementBinder binder() {
        List<Object> params = parameters;
        return statement -> {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
        };
    }
}

package com.uxplima.uxmlib.storage.sql;

/**
 * A small, safe DELETE builder. Each condition value goes through a bound {@code ?} placeholder and every
 * column is validated against the same strict allowlist as {@link SelectBuilder}, so the produced
 * {@link Query} is injection-safe by construction.
 *
 * <p><strong>A delete with no {@link #where} removes every row.</strong> That is legal SQL and sometimes
 * intended, but add a condition unless you mean to.
 *
 * <pre>{@code
 * Query q = DeleteBuilder.from("players").where("id", 5).build();
 * int rows = new Sql(database).update(q);
 * }</pre>
 */
public final class DeleteBuilder {

    private final String table;
    private final WhereClause where = new WhereClause();

    private DeleteBuilder(String table) {
        this.table = SqlIdentifiers.identifier(table, "table");
    }

    /** Start a {@code DELETE FROM table}. */
    public static DeleteBuilder from(String table) {
        return new DeleteBuilder(table);
    }

    /** Add an equality condition {@code column = ?}. Conditions are AND-combined. */
    public DeleteBuilder where(String column, Object value) {
        where.eq(column, value);
        return this;
    }

    /** Add a comparison condition {@code column <op> ?} (op e.g. {@code ">="}). */
    public DeleteBuilder where(String column, String operator, Object value) {
        where.compare(column, operator, value);
        return this;
    }

    /** Render the {@link Query}. */
    public Query build() {
        return new Query("DELETE FROM " + table + where.render(), where.parameters());
    }
}

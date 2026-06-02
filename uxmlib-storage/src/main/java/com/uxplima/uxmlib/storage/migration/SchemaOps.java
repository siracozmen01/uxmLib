package com.uxplima.uxmlib.storage.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;
import com.uxplima.uxmlib.storage.sql.SqlType;

/**
 * Admin-style column-maintenance and table-reset DDL: the primitives a "rename a stat" or "reset the season"
 * operator command needs. {@link #dropColumn}, {@link #renameColumn} and {@link #alterColumnType} are
 * dialect-aware {@code ALTER TABLE}s; {@link #copyColumnData} and {@link #wipeColumnData} are portable
 * {@code UPDATE}s that move or clear a single column's values; {@link #resetTable} is a {@code DELETE} that
 * empties a table while keeping its schema.
 *
 * <p>This is the destructive sibling of {@link SchemaIntrospector#ensureColumn} (additive). Combine the two:
 * probe with the introspector, then drop/rename here.
 *
 * <p>DDL cannot use bound parameters, so every table and column name is validated against a strict identifier
 * allowlist before it reaches a statement — anything that could carry an injection is rejected first. Native
 * {@code RENAME COLUMN} (SQLite 3.25+) and {@code DROP COLUMN} (SQLite 3.35+) are used directly; our bundled
 * sqlite-jdbc is 3.49, so the table-rebuild dance older SQLite needed is not required.
 */
public final class SchemaOps {

    // A bare identifier or one dotted qualifier — the same shape SchemaIntrospector/SelectBuilder accept.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    // A column-type fragment: type keywords plus the parens/commas/default-literal a type needs, matching the
    // allowlist SchemaIntrospector uses. No semicolons, quotes or comment markers, so it cannot inject.
    private static final Pattern COLUMN_TYPE = Pattern.compile("[A-Za-z0-9_ ,()'.+-]+");

    private final Database database;

    public SchemaOps(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Drop {@code column} from {@code table}. Uses native {@code ALTER TABLE ... DROP COLUMN}, supported across
     * SQLite 3.35+, MySQL/MariaDB, Postgres and H2.
     *
     * @throws IllegalArgumentException if a name is not a simple SQL identifier
     * @throws StorageException if the statement fails
     */
    public void dropColumn(String table, String column) {
        String tableName = identifier(table, "table");
        String columnName = identifier(column, "column");
        execute(
                "ALTER TABLE " + tableName + " DROP COLUMN " + columnName,
                "could not drop column " + tableName + "." + columnName);
    }

    /**
     * Rename {@code from} to {@code to} on {@code table}, preserving the column's data. Uses native
     * {@code ALTER TABLE ... RENAME COLUMN ... TO ...}, supported across SQLite 3.25+, MySQL 8/MariaDB 10.5+,
     * Postgres and H2.
     *
     * @throws IllegalArgumentException if a name is not a simple SQL identifier
     * @throws StorageException if the statement fails
     */
    public void renameColumn(String table, String from, String to) {
        String tableName = identifier(table, "table");
        String fromName = identifier(from, "from");
        String toName = identifier(to, "to");
        execute(
                "ALTER TABLE " + tableName + " RENAME COLUMN " + fromName + " TO " + toName,
                "could not rename column " + tableName + "." + fromName + " to " + toName);
    }

    /**
     * Copy every value from {@code fromColumn} into {@code toColumn} on the same table, overwriting whatever
     * {@code toColumn} held. Both columns must already exist (pair this with
     * {@link SchemaIntrospector#ensureColumn} to add the target first). The companion to
     * {@link #renameColumn} for the case where the old column must stay: add the new column, copy the data
     * across, then drop the old one. Returns the number of rows updated.
     *
     * <p>This is a portable {@code UPDATE}, identical across every backend, so it does not need the dialect
     * seam.
     *
     * @throws IllegalArgumentException if a name is not a simple SQL identifier
     * @throws StorageException if the statement fails
     */
    public int copyColumnData(String table, String fromColumn, String toColumn) {
        String tableName = identifier(table, "table");
        String fromName = identifier(fromColumn, "fromColumn");
        String toName = identifier(toColumn, "toColumn");
        return executeUpdate(
                "UPDATE " + tableName + " SET " + toName + " = " + fromName,
                "could not copy column data " + tableName + "." + fromName + " to " + toName);
    }

    /**
     * Clear {@code column} on every row of {@code table}, setting it back to SQL {@code NULL}. The admin
     * "wipe one stat" primitive — emptying a single column without touching the rest of the row (a per-column
     * reset, narrower than {@link #resetTable}). Returns the number of rows updated.
     *
     * <p>Setting {@code NULL} rather than re-deriving each column's declared {@code DEFAULT} is deliberate: a
     * column's default is not portably readable at runtime, and {@code NULL} is the one cleared value every
     * backend agrees on. A caller that wants a specific reset value passes it through an {@code UPDATE} of
     * their own.
     *
     * @throws IllegalArgumentException if a name is not a simple SQL identifier
     * @throws StorageException if the statement fails
     */
    public int wipeColumnData(String table, String column) {
        String tableName = identifier(table, "table");
        String columnName = identifier(column, "column");
        return executeUpdate(
                "UPDATE " + tableName + " SET " + columnName + " = NULL",
                "could not wipe column data " + tableName + "." + columnName);
    }

    /**
     * Change the declared type of an existing {@code column} on {@code table} to {@code newType}, going
     * through the {@link Dialect} seam so the backend-correct {@code ALTER COLUMN} / {@code MODIFY COLUMN}
     * clause is emitted. Accepts a {@link SqlType} (its {@link Dialect}-rendered DDL name is used) or a raw
     * type fragment such as {@code "VARCHAR(64)"}.
     *
     * <p><strong>Not portable to SQLite</strong>, which has no {@code ALTER COLUMN} and only loose type
     * affinity; the call throws {@link UnsupportedOperationException} there (see {@link Dialect#alterColumnType}).
     *
     * @throws IllegalArgumentException if a name is not a simple SQL identifier or {@code newType} is not a
     *     simple type token
     * @throws UnsupportedOperationException if the dialect cannot retype a column in place (SQLite, generic)
     * @throws StorageException if the statement fails
     */
    public void alterColumnType(String table, String column, SqlType<?> newType) {
        Objects.requireNonNull(newType, "newType");
        alterColumnType(table, column, newType.jdbcType().getName());
    }

    /**
     * Change the declared type of an existing {@code column} on {@code table} to the raw type fragment
     * {@code newType} (e.g. {@code "BIGINT"}, {@code "VARCHAR(64)"}), going through the {@link Dialect} seam.
     * See {@link #alterColumnType(String, String, SqlType)} for the {@link SqlType}-typed overload and the
     * SQLite caveat.
     *
     * @throws IllegalArgumentException if a name is not a simple SQL identifier or {@code newType} is not a
     *     simple type token
     * @throws UnsupportedOperationException if the dialect cannot retype a column in place (SQLite, generic)
     * @throws StorageException if the statement fails
     */
    public void alterColumnType(String table, String column, String newType) {
        String tableName = identifier(table, "table");
        String columnName = identifier(column, "column");
        String type = columnType(newType);
        execute(
                dialect().alterColumnType(tableName, columnName, type),
                "could not alter column type " + tableName + "." + columnName + " to " + type);
    }

    /**
     * Empty {@code table} of every row with a {@code DELETE}, keeping its schema, indexes and triggers. The
     * admin "clear/reset" primitive (a season reset). Returns the number of rows removed.
     *
     * <p>Plain {@code DELETE} (not {@code TRUNCATE}) is used deliberately: it is transactional, returns an
     * affected-row count, and behaves identically across every backend — {@code TRUNCATE}'s semantics and
     * permissions vary by dialect.
     *
     * @throws IllegalArgumentException if {@code table} is not a simple SQL identifier
     * @throws StorageException if the statement fails
     */
    public int resetTable(String table) {
        String tableName = identifier(table, "table");
        try (Connection conn = database.connection();
                Statement statement = conn.createStatement()) {
            return statement.executeUpdate("DELETE FROM " + tableName);
        } catch (SQLException failure) {
            throw new StorageException("could not reset table " + tableName, failure);
        }
    }

    private void execute(String sql, String onFailure) {
        try (Connection conn = database.connection();
                Statement statement = conn.createStatement()) {
            statement.execute(sql);
        } catch (SQLException failure) {
            throw new StorageException(onFailure, failure);
        }
    }

    private int executeUpdate(String sql, String onFailure) {
        try (Connection conn = database.connection();
                Statement statement = conn.createStatement()) {
            return statement.executeUpdate(sql);
        } catch (SQLException failure) {
            throw new StorageException(onFailure, failure);
        }
    }

    private static String identifier(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a simple SQL identifier (got '" + value + "')");
        }
        return value;
    }

    private static String columnType(String value) {
        Objects.requireNonNull(value, "newType");
        String trimmed = value.strip();
        if (trimmed.isEmpty() || !COLUMN_TYPE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("newType must be a simple SQL type token (got '" + value + "')");
        }
        return trimmed;
    }

    /** The dialect this helper targets, for callers branching on backend-specific schema. */
    public Dialect dialect() {
        return database.dialect();
    }
}

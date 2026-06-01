package com.uxplima.uxmlib.storage.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;

/**
 * Probes the live schema through {@link DatabaseMetaData} and applies additive, re-runnable column adds.
 * Unlike {@link MigrationRunner} there is no history row: {@link #ensureColumn} checks whether the column is
 * already present and only then runs {@code ALTER TABLE ... ADD COLUMN}, so calling it again is a no-op. That
 * makes it the right tool for additive schema evolution a plugin wants to converge to on every boot, while
 * the versioned {@link MigrationRunner} remains the path for ordered, destructive, or data migrations.
 *
 * <p>DDL cannot use bound parameters, so the table and column names are validated against a strict
 * identifier allowlist and the column type is restricted to an allowlisted token (letters, digits, spaces
 * and the punctuation a type fragment such as {@code VARCHAR(64) DEFAULT 0} needs) — anything that could
 * carry an injection is rejected before it reaches the statement.
 */
public final class SchemaIntrospector {

    // A bare identifier or one dotted qualifier — the same shape SelectBuilder/Identifiers accept.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    // A column-type fragment: the SQL type keywords plus the parens/commas/default-literal a type needs.
    // No semicolons, quotes or comment markers, so it cannot terminate the statement or inject another.
    private static final Pattern COLUMN_TYPE = Pattern.compile("[A-Za-z0-9_ ,()'.+-]+");

    private final Database database;

    public SchemaIntrospector(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Whether {@code table} exists in the database. */
    public boolean tableExists(String table) {
        String name = identifier(table, "table");
        try (Connection conn = database.connection();
                ResultSet tables = conn.getMetaData().getTables(conn.getCatalog(), null, name, null)) {
            return matchesIgnoringCase(tables, "TABLE_NAME", name);
        } catch (SQLException failure) {
            throw new StorageException("could not introspect table " + name, failure);
        }
    }

    /** Whether {@code column} exists on {@code table}. */
    public boolean columnExists(String table, String column) {
        String tableName = identifier(table, "table");
        String columnName = identifier(column, "column");
        try (Connection conn = database.connection();
                ResultSet columns = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return matchesIgnoringCase(columns, "COLUMN_NAME", columnName);
        } catch (SQLException failure) {
            throw new StorageException("could not introspect column " + tableName + "." + columnName, failure);
        }
    }

    /**
     * Add {@code column} of {@code columnType} to {@code table} only if it is not already present. Returns
     * {@code true} when it ran the {@code ALTER}, {@code false} when the column already existed. Safe to call
     * on every boot.
     *
     * @param columnType an allowlisted SQL type fragment, e.g. {@code "INTEGER"} or {@code "TEXT DEFAULT ''"}
     */
    public boolean ensureColumn(String table, String column, String columnType) {
        String tableName = identifier(table, "table");
        String columnName = identifier(column, "column");
        String type = columnType(columnType);
        if (columnExists(tableName, columnName)) {
            return false;
        }
        try (Connection conn = database.connection();
                Statement statement = conn.createStatement()) {
            statement.execute(addColumnSql(tableName, columnName, type));
            return true;
        } catch (SQLException failure) {
            throw new StorageException("could not add column " + tableName + "." + columnName, failure);
        }
    }

    private String addColumnSql(String table, String column, String columnType) {
        // ADD COLUMN is portable across SQLite/MySQL/Postgres; the Dialect seam is here for the points where
        // additive DDL diverges (e.g. an IF NOT EXISTS guard) so callers get the backend-correct statement.
        String add = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnType;
        return switch (database.dialect()) {
            case SQLITE, MYSQL, GENERIC -> add;
            case POSTGRES -> "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS " + column + " " + columnType;
        };
    }

    private static boolean matchesIgnoringCase(ResultSet rows, String nameColumn, String expected) throws SQLException {
        while (rows.next()) {
            if (expected.equalsIgnoreCase(rows.getString(nameColumn))) {
                return true;
            }
        }
        return false;
    }

    private static String identifier(String value, String what) {
        Objects.requireNonNull(value, what);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(what + " must be a simple SQL identifier (got '" + value + "')");
        }
        return value;
    }

    private static String columnType(String value) {
        Objects.requireNonNull(value, "columnType");
        String trimmed = value.strip();
        if (trimmed.isEmpty() || !COLUMN_TYPE.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("columnType must be a simple SQL type token (got '" + value + "')");
        }
        return trimmed;
    }

    /** The dialect this introspector targets, for callers branching on backend-specific schema. */
    public Dialect dialect() {
        return database.dialect();
    }
}

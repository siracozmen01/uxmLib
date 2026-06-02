package com.uxplima.uxmlib.storage.sql;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The SQL backend a {@link Database} speaks, inferred from its JDBC URL. Used to emit backend-correct SQL
 * where the dialects diverge — most importantly the insert-or-update (upsert) statement, which
 * SQLite/Postgres spell with {@code ON CONFLICT}, MySQL/MariaDB with {@code ON DUPLICATE KEY}, and H2 with
 * {@code MERGE INTO ... KEY(...)}.
 */
public enum Dialect {
    SQLITE,
    MYSQL,
    POSTGRES,
    H2,
    GENERIC;

    /** The dialect for {@code jdbcUrl} (read from its {@code jdbc:<backend>:} prefix), {@link #GENERIC} if unknown. */
    public static Dialect fromJdbcUrl(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:sqlite:")) {
            return SQLITE;
        }
        if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
            return MYSQL;
        }
        if (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:postgres:")) {
            return POSTGRES;
        }
        if (url.startsWith("jdbc:h2:")) {
            return H2;
        }
        return GENERIC;
    }

    /**
     * Build an upsert: insert a row, or update the non-id columns of the row that already has the same
     * {@code idColumn}. {@code columns} is every bound column in order, including {@code idColumn}.
     *
     * @throws UnsupportedOperationException if this dialect has no portable upsert (override {@code save})
     */
    public String upsert(String table, String idColumn, List<String> columns) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(idColumn, "idColumn");
        Objects.requireNonNull(columns, "columns");
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String columnList = String.join(", ", columns);
        String insert = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ")";
        List<String> updates = columns.stream().filter(c -> !c.equals(idColumn)).toList();
        return switch (this) {
            case SQLITE, POSTGRES -> insert + onConflict(idColumn, updates);
            case MYSQL -> insert + onDuplicateKey(updates, idColumn);
            case H2 -> "MERGE INTO " + table + " (" + columnList + ") KEY(" + idColumn + ") VALUES (" + placeholders
                    + ")";
            case GENERIC -> throw new UnsupportedOperationException(
                    "no portable upsert for this JDBC backend; override Repository.save()");
        };
    }

    private static String onConflict(String idColumn, List<String> updates) {
        if (updates.isEmpty()) {
            return " ON CONFLICT(" + idColumn + ") DO NOTHING";
        }
        String set = updates.stream().map(c -> c + " = excluded." + c).collect(Collectors.joining(", "));
        return " ON CONFLICT(" + idColumn + ") DO UPDATE SET " + set;
    }

    private static String onDuplicateKey(List<String> updates, String idColumn) {
        if (updates.isEmpty()) {
            return " ON DUPLICATE KEY UPDATE " + idColumn + " = " + idColumn;
        }
        String set = updates.stream().map(c -> c + " = VALUES(" + c + ")").collect(Collectors.joining(", "));
        return " ON DUPLICATE KEY UPDATE " + set;
    }

    /**
     * Build an {@code ALTER TABLE} that changes the declared type of an existing column. The clause is the one
     * piece of column-maintenance DDL that genuinely diverges by backend, so it lives behind this seam rather
     * than in a caller: Postgres spells it {@code ALTER COLUMN col TYPE newType}, H2 {@code ALTER COLUMN col
     * newType}, and MySQL/MariaDB {@code MODIFY COLUMN col newType}.
     *
     * <p>SQLite is intentionally unsupported. It has no {@code ALTER COLUMN} and its columns carry only loose
     * type <em>affinity</em>, so a type change is both impossible in one statement and largely meaningless;
     * callers that must retype a SQLite column rebuild the table instead. {@link #GENERIC} is rejected for the
     * same reason it has no portable upsert — the syntax is unknown.
     *
     * @throws UnsupportedOperationException if this dialect cannot retype a column in place (SQLite, generic)
     */
    public String alterColumnType(String table, String column, String newType) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(newType, "newType");
        String prefix = "ALTER TABLE " + table + " ";
        return switch (this) {
            case POSTGRES -> prefix + "ALTER COLUMN " + column + " TYPE " + newType;
            case H2 -> prefix + "ALTER COLUMN " + column + " " + newType;
            case MYSQL -> prefix + "MODIFY COLUMN " + column + " " + newType;
            case SQLITE -> throw new UnsupportedOperationException(
                    "SQLite has no ALTER COLUMN type change; rebuild the table to retype a column");
            case GENERIC -> throw new UnsupportedOperationException(
                    "no portable ALTER COLUMN type change for this JDBC backend");
        };
    }
}

package com.uxplima.uxmlib.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Applies a set of {@link Migration}s exactly once each, in ascending version order. Applied versions
 * are recorded in a {@code uxmlib_schema_history} table, so re-running {@link #apply(List)} is idempotent:
 * already-applied migrations are skipped and only newer ones run. Each migration runs in its own
 * transaction — a failure rolls that migration back and aborts, leaving the schema at the last good
 * version. This is intentionally simpler than Flyway: no checksums, no out-of-order handling.
 */
public final class MigrationRunner {

    private static final String HISTORY_TABLE = "uxmlib_schema_history";

    private final Database database;

    public MigrationRunner(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** Apply every migration whose version exceeds the highest already applied. Returns how many ran. */
    public int apply(List<Migration> migrations) {
        Objects.requireNonNull(migrations, "migrations");
        List<Migration> ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparingInt(Migration::version));
        try (Connection conn = database.connection()) {
            ensureHistoryTable(conn);
            int current = currentVersion(conn);
            int applied = 0;
            for (Migration migration : ordered) {
                if (migration.version() > current) {
                    applyOne(conn, migration);
                    applied++;
                }
            }
            return applied;
        } catch (SQLException failure) {
            throw new StorageException("migration failed", failure);
        }
    }

    /** The highest applied migration version, or 0 when none have run. */
    public int currentVersion() {
        try (Connection conn = database.connection()) {
            ensureHistoryTable(conn);
            return currentVersion(conn);
        } catch (SQLException failure) {
            throw new StorageException("could not read schema version", failure);
        }
    }

    private void ensureHistoryTable(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + HISTORY_TABLE
                    + " (version INTEGER PRIMARY KEY, description TEXT NOT NULL, applied_at_ms INTEGER NOT NULL)");
        }
    }

    private int currentVersion(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement();
                ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM " + HISTORY_TABLE)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private void applyOne(Connection conn, Migration migration) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (String statementSql : splitStatements(migration.sql())) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute(statementSql);
                }
            }
            recordApplied(conn, migration);
            conn.commit();
        } catch (SQLException failure) {
            conn.rollback();
            throw new SQLException(
                    "migration " + migration.version() + " (" + migration.description() + ") failed", failure);
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private void recordApplied(Connection conn, Migration migration) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "INSERT INTO " + HISTORY_TABLE + " (version, description, applied_at_ms) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        for (String part : sql.split(";")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }
}

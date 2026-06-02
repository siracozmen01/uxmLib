package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class DatabaseBuilderTest {

    @Test
    void opensAnInMemorySqliteDatabase() throws SQLException {
        try (Database db = Database.builder().sqliteInMemory().build()) {
            assertThat(db.isClosed()).isFalse();
            try (Connection conn = db.connection()) {
                assertThat(conn.isValid(1)).isTrue();
            }
        }
    }

    @Test
    void closingShutsThePoolDown() {
        Database db = Database.builder().sqliteInMemory().build();
        db.close();
        assertThat(db.isClosed()).isTrue();
    }

    @Test
    void closeIsIdempotent() {
        Database db = Database.builder().sqliteInMemory().build();
        db.close();
        db.close(); // a second close (e.g. a defensive finally) must not throw
        assertThat(db.isClosed()).isTrue();
    }

    @Test
    void pingReportsReachability() {
        Database db = Database.builder().sqliteInMemory().build();
        assertThat(db.ping()).isTrue(); // answers SELECT 1
        db.close();
        assertThat(db.ping()).isFalse(); // pool shut down -> unreachable
    }

    @Test
    void failsWhenNoBackendIsConfigured() {
        assertThatThrownBy(() -> Database.builder().build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidPoolSize() {
        assertThatThrownBy(() -> Database.builder().maxPoolSize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesADefaultSqliteBusyTimeout() throws SQLException {
        try (Database db = Database.builder().sqliteInMemory().build()) {
            assertThat(readBusyTimeout(db)).isPositive();
        }
    }

    @Test
    void appliesAConfiguredSqliteBusyTimeout() throws SQLException {
        try (Database db =
                Database.builder().sqliteInMemory().busyTimeoutMs(7500).build()) {
            assertThat(readBusyTimeout(db)).isEqualTo(7500);
        }
    }

    @Test
    void rejectsANegativeBusyTimeout() {
        assertThatThrownBy(() -> Database.builder().busyTimeoutMs(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesAConfiguredJournalMode() throws SQLException {
        // OFF is honoured even on an in-memory database, where WAL would fall back to "memory"; asserting on
        // OFF proves the PRAGMA was actually applied rather than the engine's in-memory default.
        try (Database db = Database.builder()
                .sqliteInMemory()
                .journalMode(DatabaseBuilder.JournalMode.OFF)
                .build()) {
            assertThat(readJournalMode(db)).isEqualToIgnoringCase("off");
        }
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void rejectsANullJournalMode() {
        assertThatThrownBy(() -> Database.builder().journalMode(null)).isInstanceOf(NullPointerException.class);
    }

    private static String readJournalMode(Database db) throws SQLException {
        try (Connection conn = db.connection();
                Statement statement = conn.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA journal_mode")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static int readBusyTimeout(Database db) throws SQLException {
        try (Connection conn = db.connection();
                Statement statement = conn.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA busy_timeout")) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }
}

package com.uxplima.uxmlib.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;

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
}

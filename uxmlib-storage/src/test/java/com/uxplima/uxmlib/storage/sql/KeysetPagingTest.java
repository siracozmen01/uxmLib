package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves keyset (seek) pagination streams every row exactly once in key order, across several pages,
 * against a real in-memory SQLite database.
 */
class KeysetPagingTest {

    private Database database;
    private Sql sql;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibkeyset?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE players (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private void seed(int count) {
        List<StatementBinder> binders = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int id = i;
            binders.add(ps -> {
                ps.setLong(1, id);
                ps.setString(2, "p" + id);
            });
        }
        sql.batch("INSERT INTO players (id, name) VALUES (?, ?)", binders);
    }

    @Test
    void streamsEveryRowExactlyOnceInKeyOrder() {
        seed(250);

        List<Long> seen = new ArrayList<>();
        sql.forEachByKey("players", "id", 50, row -> row.getLong("id"), seen::add);

        List<Long> expected =
                IntStream.rangeClosed(1, 250).mapToObj(Long::valueOf).toList();
        assertThat(seen).containsExactlyElementsOf(expected);
    }

    @Test
    void handlesACountThatLandsExactlyOnAPageBoundary() {
        seed(100);

        List<Long> seen = new ArrayList<>();
        sql.forEachByKey("players", "id", 50, row -> row.getLong("id"), seen::add);

        assertThat(seen).hasSize(100);
        assertThat(seen.get(0)).isEqualTo(1L);
        assertThat(seen.get(99)).isEqualTo(100L);
    }

    @Test
    void streamsNothingForAnEmptyTable() {
        List<Long> seen = new ArrayList<>();
        sql.forEachByKey("players", "id", 25, row -> row.getLong("id"), seen::add);

        assertThat(seen).isEmpty();
    }

    @Test
    void runsTheConsumerAfterTheConnectionIsReturned() {
        seed(40);

        // Borrowing a fresh connection from inside the consumer would deadlock a single-connection pool if
        // the page's connection were still held; that it does not proves the callback runs after close.
        List<Long> seen = new ArrayList<>();
        sql.forEachByKey("players", "id", 10, row -> row.getLong("id"), id -> {
            seen.add(id);
            assertThat(database.ping()).isTrue();
        });

        assertThat(seen).hasSize(40);
    }

    @Test
    void rejectsAnInjectingKeyColumn() {
        assertThatThrownBy(() ->
                        sql.forEachByKey("players", "id; DROP TABLE players", 10, row -> row.getLong(1), id -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositivePageSize() {
        assertThatThrownBy(() -> sql.forEachByKey("players", "id", 0, row -> row.getLong(1), id -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

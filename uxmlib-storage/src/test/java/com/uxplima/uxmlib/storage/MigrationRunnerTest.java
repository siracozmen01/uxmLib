package com.uxplima.uxmlib.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MigrationRunnerTest {

    private Database database;
    private MigrationRunner runner;
    private Sql sql;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibmig?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        runner = new MigrationRunner(database);
        sql = new Sql(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void appliesPendingMigrationsInOrderThenSkipsThem() {
        List<Migration> migrations = List.of(
                new Migration(1, "create players", "CREATE TABLE players (id INTEGER PRIMARY KEY, name TEXT)"),
                new Migration(2, "add coins", "ALTER TABLE players ADD COLUMN coins INTEGER NOT NULL DEFAULT 0"));

        int firstRun = runner.apply(migrations);
        assertThat(firstRun).isEqualTo(2);
        assertThat(runner.currentVersion()).isEqualTo(2);

        // The schema is usable; both columns exist.
        sql.update("INSERT INTO players (id, name, coins) VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, 1);
            ps.setString(2, "Steve");
            ps.setInt(3, 50);
        });
        assertThat(sql.queryFirst("SELECT coins FROM players WHERE id = ?", ps -> ps.setInt(1, 1), r -> r.getInt(1)))
                .contains(50);

        // Re-running is idempotent: nothing new applied.
        int secondRun = runner.apply(migrations);
        assertThat(secondRun).isZero();
    }

    @Test
    void appliesOnlyNewerMigrationsOnTheSecondRun() {
        runner.apply(List.of(new Migration(1, "v1", "CREATE TABLE a (id INTEGER PRIMARY KEY)")));
        int added = runner.apply(List.of(
                new Migration(1, "v1", "CREATE TABLE a (id INTEGER PRIMARY KEY)"),
                new Migration(2, "v2", "CREATE TABLE b (id INTEGER PRIMARY KEY)")));
        assertThat(added).isEqualTo(1);
        assertThat(runner.currentVersion()).isEqualTo(2);
    }

    @Test
    void rejectsAnInvalidMigrationVersion() {
        assertThatThrownBy(() -> new Migration(0, "bad", "SELECT 1")).isInstanceOf(IllegalArgumentException.class);
    }
}

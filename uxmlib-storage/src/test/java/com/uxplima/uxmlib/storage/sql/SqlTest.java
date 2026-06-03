package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmlib.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the JDBC helpers against a real in-memory SQLite database — no Paper, no MockBukkit, just
 * a driver on the classpath. The same connection-backed schema is created per test.
 */
class SqlTest {

    private Database database;
    private Sql sql;

    @BeforeEach
    void setUp() {
        // A shared in-memory database name keeps the schema alive across pooled connections for the test.
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibtest?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE players (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        sql.execute("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void insertsAndQueriesRows() {
        int inserted = sql.update("INSERT INTO players (id, name) VALUES (?, ?)", ps -> {
            ps.setInt(1, 1);
            ps.setString(2, "Steve");
        });
        assertThat(inserted).isEqualTo(1);

        List<String> names =
                sql.query("SELECT name FROM players ORDER BY id", StatementBinder.NONE, row -> row.getString("name"));
        assertThat(names).containsExactly("Steve");
    }

    @Test
    void queryFirstReturnsEmptyWhenNoRows() {
        Optional<String> found = sql.queryFirst(
                "SELECT name FROM players WHERE id = ?", ps -> ps.setInt(1, 99), row -> row.getString(1));
        assertThat(found).isEmpty();
    }

    @Test
    void queryFirstReturnsTheFirstRow() {
        sql.update("INSERT INTO players (id, name) VALUES (?, ?)", ps -> {
            ps.setInt(1, 7);
            ps.setString(2, "Alex");
        });
        Optional<String> found =
                sql.queryFirst("SELECT name FROM players WHERE id = ?", ps -> ps.setInt(1, 7), row -> row.getString(1));
        assertThat(found).contains("Alex");
    }

    @Test
    void insertReturningKeyHandsBackTheGeneratedId() {
        long first = sql.insertReturningKey("INSERT INTO accounts (name) VALUES (?)", ps -> ps.setString(1, "Steve"));
        long second = sql.insertReturningKey("INSERT INTO accounts (name) VALUES (?)", ps -> ps.setString(1, "Alex"));
        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);

        Optional<String> name = sql.queryFirst(
                "SELECT name FROM accounts WHERE id = ?", ps -> ps.setLong(1, second), row -> row.getString(1));
        assertThat(name).contains("Alex");
    }

    @Test
    void richSelectBuilderRunsAgainstSqlite() {
        insertPlayer(1, "Steve");
        insertPlayer(2, "Alex");
        insertPlayer(3, "Notch");

        List<String> in = sql.query(
                SelectBuilder.from("players")
                        .columns("name")
                        .whereIn("name", "Steve", "Notch")
                        .orderBy("id")
                        .build(),
                row -> row.getString("name"));
        assertThat(in).containsExactly("Steve", "Notch");

        List<String> anyOf = sql.query(
                SelectBuilder.from("players")
                        .whereAny(group -> group.eq("name", "Steve").eq("name", "Alex"))
                        .orderBy("id")
                        .build(),
                row -> row.getString("name"));
        assertThat(anyOf).containsExactly("Steve", "Alex");

        List<String> like = sql.query(
                SelectBuilder.from("players")
                        .whereLikeIgnoreCase("name", "steve")
                        .build(),
                row -> row.getString("name"));
        assertThat(like).containsExactly("Steve");

        List<String> paged = sql.query(
                SelectBuilder.from("players")
                        .columns("name")
                        .orderBy("id")
                        .limit(1)
                        .offset(1)
                        .build(),
                row -> row.getString("name"));
        assertThat(paged).containsExactly("Alex");
    }

    private void insertPlayer(int id, String name) {
        sql.update("INSERT INTO players (id, name) VALUES (?, ?)", ps -> {
            ps.setInt(1, id);
            ps.setString(2, name);
        });
    }

    @Test
    void insertReturningKeyWrapsSqlErrors() {
        assertThatThrownBy(
                        () -> sql.insertReturningKey("INSERT INTO nope (name) VALUES (?)", ps -> ps.setString(1, "x")))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void wrapsSqlErrorsInStorageException() {
        assertThatThrownBy(() -> sql.execute("SELECT * FROM table_that_does_not_exist"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void writeBuildersRoundTripAgainstSqlite() {
        long id = sql.insertReturningKey(
                InsertBuilder.into("accounts").set("name", "Steve").build());
        assertThat(id).isEqualTo(1L);

        int updated = sql.update(UpdateBuilder.table("accounts")
                .set("name", "Alex")
                .where("id", id)
                .build());
        assertThat(updated).isEqualTo(1);

        List<String> afterUpdate =
                sql.query(SelectBuilder.from("accounts").columns("name").build(), row -> row.getString("name"));
        assertThat(afterUpdate).containsExactly("Alex");

        int deleted = sql.update(DeleteBuilder.from("accounts").where("id", id).build());
        assertThat(deleted).isEqualTo(1);

        List<String> afterDelete =
                sql.query(SelectBuilder.from("accounts").columns("name").build(), row -> row.getString("name"));
        assertThat(afterDelete).isEmpty();
    }
}

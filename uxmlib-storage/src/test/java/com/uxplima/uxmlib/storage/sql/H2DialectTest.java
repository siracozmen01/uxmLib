package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmlib.storage.migration.SchemaOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Round-trips the H2 dialect against a real in-memory H2 database, proving the {@code MERGE INTO ... KEY(...)}
 * upsert and the generated-key insert actually run on the backend the {@link Dialect} string targets.
 */
class H2DialectTest {

    private Database database;
    private Sql sql;

    @BeforeEach
    void setUp() {
        // DB_CLOSE_DELAY=-1 keeps the in-memory database alive across pooled connections; a per-test name keeps
        // the JVM-global H2 registry from leaking one test's schema into the next.
        database = Database.builder()
                .jdbcUrl("jdbc:h2:mem:uxmlibh2_" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .maxPoolSize(2)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE players (uuid VARCHAR PRIMARY KEY, name VARCHAR NOT NULL, coins INT NOT NULL)");
        sql.execute("CREATE TABLE accounts (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR NOT NULL)");
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void infersTheH2Dialect() {
        assertThat(database.dialect()).isEqualTo(Dialect.H2);
    }

    @Test
    void mergeUpsertInsertsThenUpdatesInPlace() {
        List<String> columns = List.of("uuid", "name", "coins");
        String upsert = Dialect.H2.upsert("players", "uuid", columns);

        sql.update(upsert, ps -> {
            ps.setString(1, "a");
            ps.setString(2, "Steve");
            ps.setInt(3, 1);
        });
        sql.update(upsert, ps -> {
            ps.setString(1, "a");
            ps.setString(2, "Alex");
            ps.setInt(3, 2);
        });

        List<String> names = sql.query("SELECT name FROM players", StatementBinder.NONE, row -> row.getString("name"));
        assertThat(names).containsExactly("Alex");
        Optional<Integer> coins = sql.queryFirst(
                "SELECT coins FROM players WHERE uuid = ?", ps -> ps.setString(1, "a"), row -> row.getInt(1));
        assertThat(coins).contains(2);
    }

    @Test
    void insertReturningKeyHandsBackTheGeneratedId() {
        long first = sql.insertReturningKey("INSERT INTO accounts (name) VALUES (?)", ps -> ps.setString(1, "Steve"));
        long second = sql.insertReturningKey("INSERT INTO accounts (name) VALUES (?)", ps -> ps.setString(1, "Alex"));
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void alterColumnTypeRetypesAColumnInPlace() {
        // Prove the H2 ALTER COLUMN spelling actually runs and widens the column, preserving the row's data.
        sql.update("INSERT INTO players (uuid, name, coins) VALUES (?, ?, ?)", ps -> {
            ps.setString(1, "a");
            ps.setString(2, "Steve");
            ps.setInt(3, 7);
        });

        SchemaOps ops = new SchemaOps(database);
        ops.alterColumnType("players", "coins", SqlType.bigint());

        long coins = sql.queryFirst(
                        "SELECT coins FROM players WHERE uuid = ?", ps -> ps.setString(1, "a"), row -> row.getLong(1))
                .orElseThrow();
        assertThat(coins).isEqualTo(7L);
    }
}

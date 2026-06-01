package com.uxplima.uxmlib.storage.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises schema introspection and the idempotent {@code ensureColumn} against a real in-memory SQLite
 * database, matching {@link com.uxplima.uxmlib.storage.sql.SqlTest}'s driver-on-classpath style.
 */
class SchemaIntrospectorTest {

    private Database database;
    private Sql sql;
    private SchemaIntrospector schema;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibschema?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE players (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        schema = new SchemaIntrospector(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void reportsAnExistingTable() {
        assertThat(schema.tableExists("players")).isTrue();
    }

    @Test
    void reportsAMissingTable() {
        assertThat(schema.tableExists("ghosts")).isFalse();
    }

    @Test
    void reportsAnExistingColumn() {
        assertThat(schema.columnExists("players", "name")).isTrue();
    }

    @Test
    void reportsAMissingColumn() {
        assertThat(schema.columnExists("players", "coins")).isFalse();
    }

    @Test
    void ensureColumnAddsTheColumnOnce() {
        assertThat(schema.columnExists("players", "coins")).isFalse();

        boolean added = schema.ensureColumn("players", "coins", "INTEGER");

        assertThat(added).isTrue();
        assertThat(schema.columnExists("players", "coins")).isTrue();
    }

    @Test
    void ensureColumnIsANoOpTheSecondTime() {
        schema.ensureColumn("players", "coins", "INTEGER");

        boolean addedAgain = schema.ensureColumn("players", "coins", "INTEGER");

        assertThat(addedAgain).isFalse();
        assertThat(schema.columnExists("players", "coins")).isTrue();
    }

    @Test
    void ensureColumnPreservesExistingDataInTheNewColumn() {
        sql.update("INSERT INTO players (id, name) VALUES (?, ?)", ps -> {
            ps.setInt(1, 1);
            ps.setString(2, "Steve");
        });

        schema.ensureColumn("players", "coins", "INTEGER DEFAULT 0");

        Integer coins = sql.queryFirst(
                        "SELECT coins FROM players WHERE id = ?", ps -> ps.setInt(1, 1), row -> row.getInt("coins"))
                .orElseThrow();
        assertThat(coins).isZero();
    }

    @Test
    void rejectsAnInjectingTableName() {
        assertThatThrownBy(() -> schema.ensureColumn("players; DROP TABLE players", "coins", "INTEGER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectingColumnType() {
        assertThatThrownBy(() -> schema.ensureColumn("players", "coins", "INTEGER); DROP TABLE players; --"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

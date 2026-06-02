package com.uxplima.uxmlib.storage.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import com.uxplima.uxmlib.storage.sql.SqlType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the admin-style column-maintenance ops against a real in-memory SQLite database (sqlite-jdbc 3.49
 * supports native {@code RENAME COLUMN} and {@code DROP COLUMN}). Schema changes are verified through
 * {@link SchemaIntrospector}; the table reset is verified through a row count.
 */
class SchemaOpsTest {

    private Database database;
    private Sql sql;
    private SchemaIntrospector schema;
    private SchemaOps ops;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibops?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE players (id INTEGER PRIMARY KEY, name TEXT NOT NULL, coins INTEGER DEFAULT 0)");
        schema = new SchemaIntrospector(database);
        ops = new SchemaOps(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void dropColumnRemovesItFromTheSchema() {
        assertThat(schema.columnExists("players", "coins")).isTrue();

        ops.dropColumn("players", "coins");

        assertThat(schema.columnExists("players", "coins")).isFalse();
        assertThat(schema.columnExists("players", "name")).isTrue();
    }

    @Test
    void renameColumnChangesTheColumnName() {
        assertThat(schema.columnExists("players", "coins")).isTrue();

        ops.renameColumn("players", "coins", "tokens");

        assertThat(schema.columnExists("players", "coins")).isFalse();
        assertThat(schema.columnExists("players", "tokens")).isTrue();
    }

    @Test
    void renameColumnPreservesTheData() {
        sql.update("INSERT INTO players (id, name, coins) VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, 1);
            ps.setString(2, "Steve");
            ps.setInt(3, 50);
        });

        ops.renameColumn("players", "coins", "tokens");

        Integer tokens = sql.queryFirst(
                        "SELECT tokens FROM players WHERE id = ?", ps -> ps.setInt(1, 1), row -> row.getInt("tokens"))
                .orElseThrow();
        assertThat(tokens).isEqualTo(50);
    }

    @Test
    void copyColumnDataMovesValuesIntoTheTargetColumn() {
        schema.ensureColumn("players", "tokens", "INTEGER");
        sql.update("INSERT INTO players (id, name, coins) VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, 1);
            ps.setString(2, "Steve");
            ps.setInt(3, 42);
        });

        int updated = ops.copyColumnData("players", "coins", "tokens");

        assertThat(updated).isEqualTo(1);
        Integer tokens = sql.queryFirst(
                        "SELECT tokens FROM players WHERE id = ?", ps -> ps.setInt(1, 1), row -> row.getInt("tokens"))
                .orElseThrow();
        assertThat(tokens).isEqualTo(42);
    }

    @Test
    void wipeColumnDataNullsTheColumnOnEveryRow() {
        sql.update("INSERT INTO players (id, name, coins) VALUES (?, ?, ?)", ps -> {
            ps.setInt(1, 1);
            ps.setString(2, "Steve");
            ps.setInt(3, 99);
        });

        int updated = ops.wipeColumnData("players", "coins");

        assertThat(updated).isEqualTo(1);
        boolean nulled = sql.queryFirst(
                        "SELECT coins FROM players WHERE id = ?",
                        ps -> ps.setInt(1, 1),
                        row -> row.getObject("coins") == null)
                .orElseThrow();
        assertThat(nulled).isTrue();
    }

    @Test
    void alterColumnTypeIsUnsupportedOnSqlite() {
        // SQLite has no ALTER COLUMN type change, so the dialect seam refuses it rather than emitting bad SQL.
        assertThatThrownBy(() -> ops.alterColumnType("players", "coins", SqlType.bigint()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAnInjectingCopyTarget() {
        assertThatThrownBy(() -> ops.copyColumnData("players", "coins", "tokens; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetTableClearsEveryRowButKeepsTheTable() {
        sql.update("INSERT INTO players (id, name) VALUES (?, ?)", ps -> {
            ps.setInt(1, 1);
            ps.setString(2, "Steve");
        });
        sql.update("INSERT INTO players (id, name) VALUES (?, ?)", ps -> {
            ps.setInt(1, 2);
            ps.setString(2, "Alex");
        });

        int cleared = ops.resetTable("players");

        assertThat(cleared).isEqualTo(2);
        assertThat(schema.tableExists("players")).isTrue();
        long remaining = sql.queryFirst("SELECT COUNT(*) AS n FROM players", ps -> {}, row -> row.getLong("n"))
                .orElseThrow();
        assertThat(remaining).isZero();
    }

    @Test
    void rejectsAnInjectingTableName() {
        assertThatThrownBy(() -> ops.resetTable("players; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectingColumnName() {
        assertThatThrownBy(() -> ops.dropColumn("players", "coins; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectingRenameTarget() {
        assertThatThrownBy(() -> ops.renameColumn("players", "coins", "tokens; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

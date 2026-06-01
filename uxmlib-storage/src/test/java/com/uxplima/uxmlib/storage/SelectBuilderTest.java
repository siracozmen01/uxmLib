package com.uxplima.uxmlib.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Renders SQL and proves identifiers/operators are validated, not blindly inlined. */
class SelectBuilderTest {

    @Test
    void rendersAFullStatement() {
        Query q = SelectBuilder.from("players")
                .columns("name", "coins")
                .where("world", "world_nether")
                .where("coins", ">=", 100)
                .orderByDescending("coins")
                .limit(10)
                .build();
        assertThat(q.sql())
                .isEqualTo(
                        "SELECT name, coins FROM players WHERE world = ? AND coins >= ? ORDER BY coins DESC LIMIT 10");
        assertThat(q.parameters()).containsExactly("world_nether", 100);
    }

    @Test
    void selectsStarWhenNoColumns() {
        Query q = SelectBuilder.from("players").build();
        assertThat(q.sql()).isEqualTo("SELECT * FROM players");
    }

    @Test
    void allowsDottedIdentifiers() {
        Query q = SelectBuilder.from("schema.players").columns("players.name").build();
        assertThat(q.sql()).isEqualTo("SELECT players.name FROM schema.players");
    }

    @Test
    void rejectsAnInjectingTableName() {
        assertThatThrownBy(() -> SelectBuilder.from("players; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectingColumnName() {
        assertThatThrownBy(() -> SelectBuilder.from("players").columns("name) UNION SELECT password FROM admin --"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectingOrderByColumn() {
        assertThatThrownBy(() -> SelectBuilder.from("players").orderBy("coins; DELETE FROM players"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownOperator() {
        assertThatThrownBy(() -> SelectBuilder.from("players").where("coins", ">= 0 OR 1=1 --", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalisesAllowedOperators() {
        Query q = SelectBuilder.from("players").where("name", "like", "Steve%").build();
        assertThat(q.sql()).isEqualTo("SELECT * FROM players WHERE name LIKE ?");
    }
}

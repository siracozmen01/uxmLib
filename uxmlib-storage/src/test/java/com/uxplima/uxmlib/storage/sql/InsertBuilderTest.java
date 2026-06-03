package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Renders INSERT SQL and proves identifiers are validated, not blindly inlined. */
class InsertBuilderTest {

    @Test
    void rendersAnInsert() {
        Query q = InsertBuilder.into("players")
                .set("name", "Steve")
                .set("coins", 100)
                .build();
        assertThat(q.sql()).isEqualTo("INSERT INTO players (name, coins) VALUES (?, ?)");
        assertThat(q.parameters()).containsExactly("Steve", 100);
    }

    @Test
    void rejectsAnInjectingTableName() {
        assertThatThrownBy(() -> InsertBuilder.into("players; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectingColumnName() {
        assertThatThrownBy(() -> InsertBuilder.into("players").set("name) -- ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInsertWithNoValues() {
        assertThatThrownBy(() -> InsertBuilder.into("players").build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsADuplicateColumn() {
        assertThatThrownBy(() -> InsertBuilder.into("players").set("name", "a").set("name", "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

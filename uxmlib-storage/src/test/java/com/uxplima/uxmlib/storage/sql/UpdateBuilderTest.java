package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Renders UPDATE SQL with bound assignments and conditions, and validates identifiers. */
class UpdateBuilderTest {

    @Test
    void rendersAnUpdate() {
        Query q = UpdateBuilder.table("players")
                .set("coins", 100)
                .set("name", "Steve")
                .where("id", 5)
                .build();
        assertThat(q.sql()).isEqualTo("UPDATE players SET coins = ?, name = ? WHERE id = ?");
        assertThat(q.parameters()).containsExactly(100, "Steve", 5);
    }

    @Test
    void rendersAComparisonCondition() {
        Query q = UpdateBuilder.table("players")
                .set("banned", true)
                .where("level", ">=", 10)
                .build();
        assertThat(q.sql()).isEqualTo("UPDATE players SET banned = ? WHERE level >= ?");
        assertThat(q.parameters()).containsExactly(true, 10);
    }

    @Test
    void rendersAnUpdateWithoutWhere() {
        Query q = UpdateBuilder.table("players").set("coins", 0).build();
        assertThat(q.sql()).isEqualTo("UPDATE players SET coins = ?");
    }

    @Test
    void rejectsAnUpdateWithNoAssignments() {
        assertThatThrownBy(() -> UpdateBuilder.table("players").where("id", 5).build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnInjectingTableName() {
        assertThatThrownBy(() -> UpdateBuilder.table("players; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInjectingColumnName() {
        assertThatThrownBy(() -> UpdateBuilder.table("players").set("coins) -- ", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

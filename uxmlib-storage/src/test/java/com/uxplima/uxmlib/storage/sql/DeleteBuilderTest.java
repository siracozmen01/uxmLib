package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Renders DELETE SQL with bound conditions, and validates identifiers. */
class DeleteBuilderTest {

    @Test
    void rendersADelete() {
        Query q = DeleteBuilder.from("players").where("id", 5).build();
        assertThat(q.sql()).isEqualTo("DELETE FROM players WHERE id = ?");
        assertThat(q.parameters()).containsExactly(5);
    }

    @Test
    void rendersADeleteWithComparison() {
        Query q = DeleteBuilder.from("players").where("coins", "<", 0).build();
        assertThat(q.sql()).isEqualTo("DELETE FROM players WHERE coins < ?");
        assertThat(q.parameters()).containsExactly(0);
    }

    @Test
    void rendersADeleteWithoutWhere() {
        Query q = DeleteBuilder.from("players").build();
        assertThat(q.sql()).isEqualTo("DELETE FROM players");
    }

    @Test
    void rejectsAnInjectingTableName() {
        assertThatThrownBy(() -> DeleteBuilder.from("players; DROP TABLE players"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

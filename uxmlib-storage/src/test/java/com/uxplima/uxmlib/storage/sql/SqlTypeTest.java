package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pure codec round-trip checks plus an in-memory SQLite end-to-end, so the same {@link SqlType}
 * declares how a value binds onto a statement and reads back from a row.
 */
class SqlTypeTest {

    private Database database;
    private Sql sql;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .jdbcUrl("jdbc:sqlite:file:uxmlibtypetest?mode=memory&cache=shared")
                .maxPoolSize(1)
                .build();
        sql = new Sql(database);
        sql.execute("CREATE TABLE vals (id INTEGER PRIMARY KEY, payload TEXT, ts INTEGER)");
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void primitivesCarryTheirJdbcKindAndReadClass() {
        assertThat(SqlType.text().jdbcType()).isEqualTo(JDBCType.VARCHAR);
        assertThat(SqlType.text().type()).isEqualTo(String.class);
        assertThat(SqlType.bigint().jdbcType()).isEqualTo(JDBCType.BIGINT);
        assertThat(SqlType.bigint().type()).isEqualTo(Long.class);
    }

    @Test
    void uuidIsStoredAsTextAndReadsBack() {
        UUID id = UUID.randomUUID();
        sql.update("INSERT INTO vals (id, payload) VALUES (?, ?)", ps -> {
            ps.setInt(1, 1);
            SqlType.uuid().bind(ps, 2, id);
        });
        UUID back = sql.queryFirst(
                        "SELECT payload FROM vals WHERE id = ?",
                        ps -> ps.setInt(1, 1),
                        row -> require(SqlType.uuid().read(row, "payload")))
                .orElseThrow();
        assertThat(back).isEqualTo(id);
        assertThat(SqlType.uuid().jdbcType()).isEqualTo(JDBCType.VARCHAR);
    }

    @Test
    void instantIsStoredAsEpochMillisLong() {
        Instant now = Instant.ofEpochMilli(1_700_000_000_123L);
        sql.update("INSERT INTO vals (id, ts) VALUES (?, ?)", ps -> {
            ps.setInt(1, 2);
            SqlType.instant().bind(ps, 2, now);
        });
        Instant back = sql.queryFirst(
                        "SELECT ts FROM vals WHERE id = ?",
                        ps -> ps.setInt(1, 2),
                        row -> require(SqlType.instant().read(row, "ts")))
                .orElseThrow();
        assertThat(back).isEqualTo(now);
        assertThat(SqlType.instant().jdbcType()).isEqualTo(JDBCType.BIGINT);
    }

    @Test
    void componentRoundTripsThroughMiniMessageText() {
        Component message = Component.text("Hi", NamedTextColor.RED);
        sql.update("INSERT INTO vals (id, payload) VALUES (?, ?)", ps -> {
            ps.setInt(1, 3);
            SqlType.component().bind(ps, 2, message);
        });
        Component back = sql.queryFirst(
                        "SELECT payload FROM vals WHERE id = ?",
                        ps -> ps.setInt(1, 3),
                        row -> require(SqlType.component().read(row, "payload")))
                .orElseThrow();
        // Compare on rendered MiniMessage so we assert the persisted form, not Component identity.
        assertThat(SqlType.component().write(back))
                .isEqualTo(SqlType.component().write(message));
    }

    @Test
    void andThenComposesOverABasePrimitive() {
        SqlType<List<String>> csv =
                SqlType.text().andThen(s -> List.of(s.split(",")), parts -> String.join(",", parts), null);
        sql.update("INSERT INTO vals (id, payload) VALUES (?, ?)", ps -> {
            ps.setInt(1, 4);
            csv.bind(ps, 2, List.of("a", "b", "c"));
        });
        List<String> back = sql.queryFirst(
                        "SELECT payload FROM vals WHERE id = ?",
                        ps -> ps.setInt(1, 4),
                        row -> require(csv.read(row, "payload")))
                .orElseThrow();
        assertThat(back).containsExactly("a", "b", "c");
        assertThat(csv.jdbcType()).isEqualTo(JDBCType.VARCHAR);
    }

    @Test
    void nullValueBindsAndReadsBackAsNull() {
        sql.update("INSERT INTO vals (id, payload) VALUES (?, ?)", ps -> {
            ps.setInt(1, 5);
            SqlType.uuid().bind(ps, 2, null);
        });
        boolean wasNull = sql.query(
                        "SELECT payload FROM vals WHERE id = ?",
                        ps -> ps.setInt(1, 5),
                        row -> SqlType.uuid().read(row, "payload") == null)
                .get(0);
        assertThat(wasNull).isTrue();
    }

    @Test
    void derivedCodecsAdvertiseTheirReadClass() {
        assertThat(SqlType.uuid().type()).isEqualTo(UUID.class);
        assertThat(SqlType.instant().type()).isEqualTo(Instant.class);
        assertThat(SqlType.component().type()).isEqualTo(Component.class);
    }

    @Test
    void bindRejectsAZeroBasedIndex() {
        // The index guard fires before the statement is touched, so a stub statement suffices.
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        assertThatIllegalArgumentException().isThrownBy(() -> SqlType.text().bind(statement, 0, "v"));
    }

    /** Fail loudly instead of letting a {@code null} leak into the non-null {@link RowMapper} contract. */
    private static <T> T require(@Nullable T value) {
        return Objects.requireNonNull(value, "column value");
    }
}

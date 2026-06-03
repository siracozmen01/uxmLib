package com.uxplima.uxmlib.storage.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Parses Flyway-style {@code V<version>__<description>.sql} resource names. */
class MigrationFileTest {

    @Test
    void parsesAVersionedName() {
        Optional<MigrationFile> file = MigrationFile.parse("V3__chat_history_fts.sql");
        assertThat(file).isPresent();
        assertThat(file.get().version()).isEqualTo(3);
        assertThat(file.get().description()).isEqualTo("chat history fts");
        assertThat(file.get().fileName()).isEqualTo("V3__chat_history_fts.sql");
    }

    @Test
    void ignoresANonMigrationName() {
        assertThat(MigrationFile.parse("README.md")).isEmpty();
        assertThat(MigrationFile.parse("V3_single_underscore.sql")).isEmpty();
        assertThat(MigrationFile.parse("VX__non_numeric.sql")).isEmpty();
        assertThat(MigrationFile.parse("V3__missing_suffix")).isEmpty();
    }

    @Test
    void isCaseInsensitiveOnTheExtension() {
        assertThat(MigrationFile.parse("V1__init.SQL")).isPresent();
    }
}

package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

class HoconConfigTest {

    @Test
    void readsTypedScalarsByDottedPath(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "storage { backend = \"sqlite\", pool-size = 8 }\nenabled = true\n");

        HoconConfig config = HoconConfig.load(file);

        assertThat(config.getString("storage.backend", "?")).isEqualTo("sqlite");
        assertThat(config.getInt("storage.pool-size", -1)).isEqualTo(8);
        assertThat(config.getBoolean("enabled", false)).isTrue();
    }

    @Test
    void returnsFallbacksForAMissingFile(@TempDir Path dir) {
        HoconConfig config = HoconConfig.load(dir.resolve("absent.conf"));

        assertThat(config.getString("anything", "fallback")).isEqualTo("fallback");
        assertThat(config.getInt("nope", 42)).isEqualTo(42);
        assertThat(config.getString("missing")).isEmpty();
    }

    @Test
    void mapsASubtreeOntoARecord(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        // Configurate's default object mapper maps the camelCase record component "poolSize" to the
        // kebab-case HOCON key "pool-size".
        Files.writeString(file, "storage { backend = \"mysql\", pool-size = 16 }\n");

        HoconConfig config = HoconConfig.load(file);
        Storage storage = config.getNode("storage", Storage.class, new Storage("sqlite", 8));

        assertThat(storage.backend()).isEqualTo("mysql");
        assertThat(storage.poolSize()).isEqualTo(16);
    }

    @Test
    void reloadsAtomicallyAfterAFileChange(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "value = 1\n");
        HoconConfig config = HoconConfig.load(file);
        assertThat(config.getInt("value", -1)).isEqualTo(1);

        Files.writeString(file, "value = 2\n");
        config.reload();

        assertThat(config.getInt("value", -1)).isEqualTo(2);
    }

    @ConfigSerializable
    record Storage(String backend, int poolSize) {}
}

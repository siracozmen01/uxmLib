package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Multi-file include: an included tree fills gaps; the base config wins for keys present in both. */
class ConfigIncludeTest {

    @Test
    void baseWinsAndIncludeFillsGaps(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.conf");
        Files.writeString(base, "name = base\nstorage { backend = sqlite }\n");
        Path extra = dir.resolve("extra.conf");
        Files.writeString(extra, "name = override\nstorage { pool-size = 16 }\nmotd = hi\n");

        HoconConfig config = HoconConfig.load(base);
        boolean changed = config.include(HoconConfig.load(extra));

        assertThat(changed).isTrue();
        assertThat(config.getString("name", "?")).isEqualTo("base"); // base wins
        assertThat(config.getString("storage.backend", "?")).isEqualTo("sqlite"); // base kept
        assertThat(config.getInt("storage.pool-size", -1)).isEqualTo(16); // include filled
        assertThat(config.getString("motd", "?")).isEqualTo("hi"); // include filled
    }

    @Test
    void includeFromAPathMergesTheFilesTree(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.conf");
        Files.writeString(base, "a = 1\n");
        Path extra = dir.resolve("extra.conf");
        Files.writeString(extra, "a = 9\nb = 2\n");

        HoconConfig config = HoconConfig.load(base);
        config.include(extra);

        assertThat(config.getInt("a", -1)).isEqualTo(1); // base wins
        assertThat(config.getInt("b", -1)).isEqualTo(2); // include filled
    }

    @Test
    void includingNothingNewReportsNoChange(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.conf");
        Files.writeString(base, "a = 1\nb = 2\n");
        Path extra = dir.resolve("extra.conf");
        Files.writeString(extra, "a = 9\n");

        HoconConfig config = HoconConfig.load(base);

        assertThat(config.include(HoconConfig.load(extra))).isFalse();
    }
}

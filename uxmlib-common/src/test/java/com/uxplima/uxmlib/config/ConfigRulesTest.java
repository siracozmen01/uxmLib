package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the aggregating validation layer — every violation reported, not just the first. */
class ConfigRulesTest {

    private static HoconConfig config(Path dir, String body) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, body);
        return HoconConfig.load(file);
    }

    @Test
    void passesWhenEverythingIsValid(@TempDir Path dir) throws Exception {
        HoconConfig config = config(dir, "name = server\nlimit = 5\nmode = survival\n");
        ValidationResult result = new ConfigRules()
                .require("name")
                .range("limit", 1, 10)
                .oneOf("mode", "survival", "creative")
                .validate(config);

        assertThat(result.ok()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void collectsEveryViolationAtOnce(@TempDir Path dir) throws Exception {
        HoconConfig config = config(dir, "limit = 99\nmode = adventure\n"); // name missing, limit too big, bad mode
        ValidationResult result = new ConfigRules()
                .require("name")
                .range("limit", 1, 10)
                .oneOf("mode", "survival", "creative")
                .validate(config);

        assertThat(result.ok()).isFalse();
        assertThat(result.violations()).hasSize(3); // all three failures, not just the first
        assertThat(result.violations().stream().map(ConfigViolation::path))
                .containsExactlyInAnyOrder("name", "limit", "mode");
    }

    @Test
    void matchesRegex(@TempDir Path dir) throws Exception {
        HoconConfig config = config(dir, "host = \"not a host!\"\n");
        ValidationResult result = new ConfigRules().matches("host", "[a-z.]+").validate(config);
        assertThat(result.violations()).hasSize(1);
    }

    @Test
    void throwIfInvalidListsEveryProblem(@TempDir Path dir) throws Exception {
        HoconConfig config = config(dir, "limit = 99\n");
        ValidationResult result =
                new ConfigRules().require("name").range("limit", 1, 10).validate(config);

        assertThatThrownBy(result::throwIfInvalid)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("limit");
    }
}

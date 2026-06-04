package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@org.jspecify.annotations.NullUnmarked
class RecordConfigTest {

    @ConfigSerializable
    static final class Demo {
        String name = "default";
        int count = 0;
    }

    @Test
    void initial_load_reads_file(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 42\n");

        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        assertThat(store.current().name).isEqualTo("alice");
        assertThat(store.current().count).isEqualTo(42);
    }

    @Test
    void initial_load_falls_back_to_default_when_file_missing(@TempDir Path tmp) {
        Path file = tmp.resolve("absent.conf");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);
        assertThat(store.current().name).isEqualTo("default");
    }

    @Test
    void reload_picks_up_changes(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 1\n");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        Files.writeString(file, "name = \"bob\"\ncount = 2\n");
        Demo reloaded = store.reload();

        assertThat(reloaded.name).isEqualTo("bob");
        assertThat(store.current().name).isEqualTo("bob");
        assertThat(store.current().count).isEqualTo(2);
    }

    @Test
    void reload_with_invalid_file_throws_and_retains_prior(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 1\n");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        Files.writeString(file, "name = \"unterminated"); // invalid HOCON
        assertThatThrownBy(store::reload).isInstanceOf(ConfigException.class);

        // Prior snapshot retained.
        assertThat(store.current().name).isEqualTo("alice");
        assertThat(store.current().count).isEqualTo(1);
    }

    @Test
    void dryRun_parses_without_advancing_snapshot(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 1\n");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        Files.writeString(file, "name = \"bob\"\ncount = 2\n");
        Demo parsed = store.dryRun();

        assertThat(parsed.name).isEqualTo("bob"); // the dry-run sees the new file...
        assertThat(store.current().name).isEqualTo("alice"); // ...but current() still holds the old snapshot
    }

    @Test
    void dryRun_with_invalid_file_throws_without_advancing_snapshot(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 1\n");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        Files.writeString(file, "name = \"unterminated");
        assertThatThrownBy(store::dryRun).isInstanceOf(ConfigException.class);
        assertThat(store.current().name).isEqualTo("alice");
    }

    @Test
    void is_modified_since_load_false_immediately_after_load(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 1\n");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        assertThat(store.isModifiedSinceLoad()).isFalse();
    }

    @Test
    void is_modified_since_load_true_after_edit(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 1\n");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        // Bump mtime explicitly — some filesystems have second-level mtime resolution that would race a
        // same-second write-then-check.
        FileTime later = FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000);
        Files.writeString(file, "name = \"bob\"\ncount = 2\n");
        Files.setLastModifiedTime(file, later);

        assertThat(store.isModifiedSinceLoad()).isTrue();
    }

    @Test
    void is_modified_since_load_resets_after_reload(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 1\n");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);

        FileTime later = FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000);
        Files.writeString(file, "name = \"bob\"\ncount = 2\n");
        Files.setLastModifiedTime(file, later);
        assertThat(store.isModifiedSinceLoad()).isTrue();

        store.reload();
        assertThat(store.isModifiedSinceLoad()).isFalse();
    }

    @Test
    void is_modified_since_load_false_when_file_missing(@TempDir Path tmp) {
        Path file = tmp.resolve("absent.conf");
        RecordConfig<Demo> store = new RecordConfig<>(file, Demo.class, Demo::new);
        assertThat(store.isModifiedSinceLoad()).isFalse();
    }

    @Test
    void loadFrom_parses_a_clean_file(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"alice\"\ncount = 7\n");

        Demo value = RecordConfig.loadFrom(file, Demo.class, Demo::new);

        assertThat(value.name).isEqualTo("alice");
        assertThat(value.count).isEqualTo(7);
    }

    @Test
    void loadFrom_throws_for_an_invalid_file(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("demo.conf");
        Files.writeString(file, "name = \"unterminated");

        assertThatThrownBy(() -> RecordConfig.loadFrom(file, Demo.class, Demo::new))
                .isInstanceOf(ConfigException.class);
    }

    @Test
    void loadFrom_returns_default_when_file_missing(@TempDir Path tmp) {
        Path file = tmp.resolve("absent.conf");
        Demo value = RecordConfig.loadFrom(file, Demo.class, Demo::new);
        assertThat(value.name).isEqualTo("default");
    }
}

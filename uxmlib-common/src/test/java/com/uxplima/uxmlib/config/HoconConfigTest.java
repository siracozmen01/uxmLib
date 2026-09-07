package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
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

    @Test
    void savesEditsAtomicallyLeavingNoTempFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "value = 1\n");
        HoconConfig config = HoconConfig.load(file);
        config.root().node("value").set(2);
        config.root().node("added").set("new");

        config.save();

        HoconConfig reread = HoconConfig.load(file);
        assertThat(reread.getInt("value", -1)).isEqualTo(2);
        assertThat(reread.getString("added", "?")).isEqualTo("new");
        assertThat(Files.exists(file.resolveSibling("config.conf.tmp"))).isFalse();
    }

    @Test
    void reloadHoldsTheSameLockAsTheInPlaceMutators(@TempDir Path dir) throws Exception {
        // reload() must be synchronized like save()/interpolate() so its whole-tree swap cannot race an
        // in-place edit. Prove it by checking another thread's save() cannot enter while reload runs a
        // listener: the save would corrupt the swap if reload held no lock.
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "value = 1\n");
        HoconConfig config = HoconConfig.load(file);

        java.util.concurrent.atomic.AtomicBoolean saveCompleted = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean saveSeenInsideListener =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<Thread> started =
                new java.util.concurrent.atomic.AtomicReference<>();
        config.onReload(() -> {
            Thread saver = new Thread(() -> {
                config.save();
                saveCompleted.set(true);
            });
            started.set(saver);
            saver.start();
            try {
                // The saver is blocked on the config monitor reload holds; give it ample time to (not) run.
                saver.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Record, while still inside reload's monitor, whether the saver managed to complete.
            saveSeenInsideListener.set(saveCompleted.get());
        });

        config.reload();
        // The saver could not have acquired the lock while the listener (inside reload's monitor) ran.
        assertThat(saveSeenInsideListener).isFalse();
        // It does get the lock once reload releases it, and then writes into the temp directory. Wait for
        // that write here: leaving it running raced JUnit deleting the directory out from under it.
        Objects.requireNonNull(started.get(), "the reload listener never ran").join(5_000L);
    }

    @Test
    void savesToANewFileWhenNoneExists(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("fresh.conf");
        HoconConfig config = HoconConfig.load(file);
        config.root().node("created").set(true);

        config.save();

        assertThat(Files.exists(file)).isTrue();
        assertThat(HoconConfig.load(file).getBoolean("created", false)).isTrue();
    }

    @Test
    @DisplayName("a read leaves the tree alone, so a fallback never lands in an operator's file")
    void areadDoesNotWriteTheFallbackIntoTheTree(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "commands { example { subcommands { buy = false } } }\n");
        HoconConfig config = HoconConfig.load(file);

        // Configurate copies a default into the tree as it hands it back, unless it is told not to. With it
        // on, this first read replaces the operator's `buy = false` with a map, and the second then replaces
        // that map with `true`: the answer they wrote is gone before anybody asks for it.
        config.getString("commands.example.subcommands.buy.name", "buy");

        assertThat(config.getBoolean("commands.example.subcommands.buy", true)).isFalse();
        assertThat(config.root().raw().toString()).doesNotContain("name=buy");
    }

    @Test
    @DisplayName("a save after a read writes back what the file said, and nothing the reader defaulted")
    void asaveAfterAReadWritesNoDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "kept = 1\n");
        HoconConfig config = HoconConfig.load(file);
        config.getString("never-written", "default");
        config.getInt("also-never", 7);

        config.save();

        assertThat(Files.readString(file)).doesNotContain("never-written").doesNotContain("also-never");
    }

    @ConfigSerializable
    record Storage(String backend, int poolSize) {}
}

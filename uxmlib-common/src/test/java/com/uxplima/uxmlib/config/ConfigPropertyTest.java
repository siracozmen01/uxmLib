package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigPropertyTest {

    @Test
    void boundPropertyReflectsTheCurrentValue(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "limit = 5\n");
        HoconConfig config = HoconConfig.load(file);

        ConfigProperty<Integer> limit = config.intProperty("limit", 1);
        assertThat(limit.get()).isEqualTo(5);
    }

    @Test
    void propertyRefreshesAndFiresOnChangeAfterReload(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "limit = 5\n");
        HoconConfig config = HoconConfig.load(file);

        ConfigProperty<Integer> limit = config.intProperty("limit", 1);
        AtomicInteger observed = new AtomicInteger(-1);
        limit.onChange(observed::set);

        Files.writeString(file, "limit = 9\n");
        config.reload();

        assertThat(limit.get()).isEqualTo(9);
        assertThat(observed.get()).isEqualTo(9);
    }

    @Test
    void onChangeDoesNotFireWhenValueIsUnchanged(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "limit = 5\n");
        HoconConfig config = HoconConfig.load(file);

        ConfigProperty<Integer> limit = config.intProperty("limit", 1);
        AtomicInteger fires = new AtomicInteger();
        limit.onChange(value -> fires.incrementAndGet());

        config.reload(); // file unchanged
        assertThat(fires.get()).isZero();
    }

    @Test
    void reloadListenerRuns(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "a = 1\n");
        HoconConfig config = HoconConfig.load(file);

        AtomicInteger reloads = new AtomicInteger();
        config.onReload(reloads::incrementAndGet);

        config.reload();
        config.reload();
        assertThat(reloads.get()).isEqualTo(2);
    }

    @Test
    void aThrowingChangeListenerDoesNotStopTheOthers(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "limit = 5\n");
        HoconConfig config = HoconConfig.load(file);

        ConfigProperty<Integer> limit = config.intProperty("limit", 1);
        AtomicInteger second = new AtomicInteger(-1);
        limit.onChange(value -> {
            throw new IllegalStateException("boom");
        });
        limit.onChange(second::set);

        Files.writeString(file, "limit = 9\n");
        config.reload();

        // The first listener threw, but the second still observed the change.
        assertThat(second.get()).isEqualTo(9);
        assertThat(limit.get()).isEqualTo(9);
    }

    @Test
    void aThrowingReloadListenerDoesNotStopTheOthers(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "a = 1\n");
        HoconConfig config = HoconConfig.load(file);

        AtomicInteger second = new AtomicInteger();
        config.onReload(() -> {
            throw new IllegalStateException("boom");
        });
        config.onReload(second::incrementAndGet);

        config.reload();

        assertThat(second.get()).isEqualTo(1);
    }
}

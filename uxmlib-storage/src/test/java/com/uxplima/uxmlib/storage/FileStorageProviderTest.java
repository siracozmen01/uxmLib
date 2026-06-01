package com.uxplima.uxmlib.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/** Covers the flat-file (HOCON-per-aggregate) storage provider. */
class FileStorageProviderTest {

    @ConfigSerializable
    record Home(String name, int x, int z) {}

    private static StorageProvider<String, Home> provider(Path dir) {
        return new FileStorageProvider<>(dir, Home.class, Home::name);
    }

    @Test
    void savesAndReadsBackById(@TempDir Path dir) {
        StorageProvider<String, Home> homes = provider(dir);
        homes.save(new Home("base", 10, 20));

        assertThat(homes.findById("base")).get().extracting(Home::x).isEqualTo(10);
        assertThat(homes.findById("missing")).isEmpty();
    }

    @Test
    void saveReplacesOnTheSameId(@TempDir Path dir) {
        StorageProvider<String, Home> homes = provider(dir);
        homes.save(new Home("base", 1, 1));
        homes.save(new Home("base", 99, 99));

        assertThat(homes.findAll()).hasSize(1);
        assertThat(homes.findById("base")).get().extracting(Home::x).isEqualTo(99);
    }

    @Test
    void findAllAndDelete(@TempDir Path dir) {
        StorageProvider<String, Home> homes = provider(dir);
        homes.save(new Home("a", 1, 1));
        homes.save(new Home("b", 2, 2));
        assertThat(homes.findAll()).hasSize(2);

        assertThat(homes.deleteById("a")).isTrue();
        assertThat(homes.findAll()).hasSize(1);
        assertThat(homes.deleteById("a")).isFalse();
    }
}

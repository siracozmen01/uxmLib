package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/** Covers list mapping, section (named-table) mapping, key listing, and dot-safe node access. */
class ConfigSectionsTest {

    @ConfigSerializable
    record Kit(int cooldown, String icon) {}

    private static HoconConfig config(Path dir, String body) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, body);
        return HoconConfig.load(file);
    }

    @Test
    void mapsAListOfScalars(@TempDir Path dir) throws Exception {
        HoconConfig config = config(dir, "worlds = [ world, world_nether, world_the_end ]\n");
        List<String> worlds = config.getList("worlds", String.class);
        assertThat(worlds).containsExactly("world", "world_nether", "world_the_end");
    }

    @Test
    void mapsASectionOfNamedObjects(@TempDir Path dir) throws Exception {
        HoconConfig config = config(
                dir, "kits {\n  starter { cooldown = 60, icon = STONE }\n  vip { cooldown = 10, icon = DIAMOND }\n}\n");

        Map<String, Kit> kits = config.getSection("kits", Kit.class);

        assertThat(kits).containsOnlyKeys("starter", "vip");
        assertThat(kits).extractingByKey("starter").extracting(Kit::cooldown).isEqualTo(60);
        assertThat(kits).extractingByKey("vip").extracting(Kit::icon).isEqualTo("DIAMOND");
    }

    @Test
    void listsSectionKeys(@TempDir Path dir) throws Exception {
        HoconConfig config = config(dir, "kits {\n  a { x = 1 }\n  b { x = 1 }\n  c { x = 1 }\n}\n");
        assertThat(config.keys("kits")).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void nodeAtReachesADottedKey(@TempDir Path dir) throws Exception {
        // A key literally containing a dot cannot be addressed by the dotted-string reads.
        HoconConfig config = config(dir, "\"server.name\" = hub\n");
        assertThat(config.nodeAt("server.name").getString()).isEqualTo("hub");
    }
}

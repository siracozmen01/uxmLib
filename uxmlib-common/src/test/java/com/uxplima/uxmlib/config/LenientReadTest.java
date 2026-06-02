package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/**
 * The lenient read path: one malformed entry in a list or named section is skipped (and the failure is
 * surfaced) while every well-formed entry still parses, so a single bad row never wipes a whole section.
 */
class LenientReadTest {

    @ConfigSerializable
    record Kit(int cooldown, String icon) {}

    private static HoconConfig config(Path dir, String body) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, body);
        return HoconConfig.load(file);
    }

    @Test
    void skipsOneBadSectionEntryAndKeepsTheGoodOnes(@TempDir Path dir) throws Exception {
        // The "broken" kit has a non-numeric cooldown, so mapping it throws; the other two must survive.
        HoconConfig config = config(
                dir,
                """
                kits {
                  starter { cooldown = 60, icon = STONE }
                  broken { cooldown = "oops", icon = DIRT }
                  vip { cooldown = 10, icon = DIAMOND }
                }
                """);

        LenientResult<Map<String, Kit>> result = config.getSectionLenient("kits", Kit.class);

        assertThat(result.value()).containsOnlyKeys("starter", "vip");
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).path()).isEqualTo("kits.broken");
    }

    @Test
    void skipsOneBadListElementAndKeepsTheGoodOnes(@TempDir Path dir) throws Exception {
        HoconConfig config = config(
                dir,
                """
                kits = [
                  { cooldown = 5, icon = STONE },
                  { cooldown = "oops", icon = DIRT },
                  { cooldown = 9, icon = DIAMOND }
                ]
                """);

        LenientResult<List<Kit>> result = config.getListLenient("kits", Kit.class);

        assertThat(result.value()).hasSize(2);
        assertThat(result.value()).extracting(Kit::cooldown).containsExactly(5, 9);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).path()).isEqualTo("kits[1]");
    }

    @Test
    void anAllGoodSectionReportsNoSkips(@TempDir Path dir) throws Exception {
        HoconConfig config =
                config(dir, "kits {\n  a { cooldown = 1, icon = STONE }\n  b { cooldown = 2, icon = DIRT }\n}\n");

        LenientResult<Map<String, Kit>> result = config.getSectionLenient("kits", Kit.class);

        assertThat(result.value()).containsOnlyKeys("a", "b");
        assertThat(result.skipped()).isEmpty();
        assertThat(result.allParsed()).isTrue();
    }

    @Test
    void anAbsentSectionIsEmptyNotAFailure(@TempDir Path dir) throws Exception {
        HoconConfig config = config(dir, "other = 1\n");

        LenientResult<Map<String, Kit>> result = config.getSectionLenient("kits", Kit.class);

        assertThat(result.value()).isEmpty();
        assertThat(result.allParsed()).isTrue();
    }
}

package com.uxplima.uxmlib.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Pure tests over an in-memory node tree for variable interpolation: a nested {@code ${a.b}} reference,
 * a value drawn from a supplied variables map, and cycle detection on a two-node loop.
 */
class ConfigInterpolationTest {

    private static ConfigurationNode node() {
        return BasicConfigurationNode.root();
    }

    @Test
    void resolvesANestedReferenceAgainstTheSameTree() throws Exception {
        ConfigurationNode root = node();
        root.node("a", "b").set("world");
        root.node("greeting").set("hello ${a.b}");

        ConfigInterpolation.interpolate(root, Map.of());

        assertThat(root.node("greeting").getString()).isEqualTo("hello world");
    }

    @Test
    void resolvesAReferenceFromTheSuppliedVariablesMap() throws Exception {
        ConfigurationNode root = node();
        root.node("line").set("on ${server.name}");

        ConfigInterpolation.interpolate(root, Map.of("server.name", "lobby"));

        assertThat(root.node("line").getString()).isEqualTo("on lobby");
    }

    @Test
    void resolvesAChainedReferenceBeforeUse() throws Exception {
        ConfigurationNode root = node();
        root.node("a").set("${b}");
        root.node("b").set("${c}");
        root.node("c").set("deep");

        ConfigInterpolation.interpolate(root, Map.of());

        assertThat(root.node("a").getString()).isEqualTo("deep");
    }

    @Test
    void detectsATwoNodeCycle() throws Exception {
        ConfigurationNode root = node();
        root.node("a").set("${b}");
        root.node("b").set("${a}");

        assertThatThrownBy(() -> ConfigInterpolation.interpolate(root, Map.of()))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void leavesUnknownReferencesUntouched() throws Exception {
        ConfigurationNode root = node();
        root.node("x").set("keep ${missing.key} here");

        ConfigInterpolation.interpolate(root, Map.of());

        assertThat(root.node("x").getString()).isEqualTo("keep ${missing.key} here");
    }

    @Test
    void getInterpolatedResolvesASingleReadWithoutMutating(@TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("config.conf");
        java.nio.file.Files.writeString(file, "host = example.com\nurl = \"https://${host}/path\"\n");
        HoconConfig config = HoconConfig.load(file);

        assertThat(config.getInterpolated("url", "?")).isEqualTo("https://example.com/path");
        // The read does not rewrite the stored value.
        assertThat(config.getString("url", "?")).isEqualTo("https://${host}/path");
    }

    @Test
    void interpolatePassRewritesTheWholeTree(@TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("config.conf");
        java.nio.file.Files.writeString(file, "name = lobby\nmotd = \"welcome to ${name}\"\n");
        HoconConfig config = HoconConfig.load(file);

        config.interpolate(Map.of());

        assertThat(config.getString("motd", "?")).isEqualTo("welcome to lobby");
    }
}

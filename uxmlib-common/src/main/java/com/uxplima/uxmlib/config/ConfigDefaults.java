package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Seeds a config file from a default bundled in the plugin jar. On first run the on-disk file does not
 * exist, so the authored, comment-rich template shipped as a classpath resource is copied to the data
 * folder byte-for-byte; an existing file is never touched, so a user's edits always survive. The copy is
 * written to a temporary file and atomically moved into place, so a crash mid-write can't leave a
 * half-seeded config.
 */
final class ConfigDefaults {

    private ConfigDefaults() {}

    /** Copy {@code resource} to {@code target} if {@code target} is absent; returns whether it was seeded. */
    static boolean extractIfAbsent(Path target, String resource, ClassLoader loader) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(loader, "loader");
        if (Files.exists(target)) {
            return false;
        }
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ConfigException("default resource not found on the classpath: " + resource);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = tempBeside(target);
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException failure) {
            throw new ConfigException("failed to extract default config " + resource, failure);
        }
    }

    private static Path tempBeside(Path target) {
        Path parent = target.getParent();
        String name = target.getFileName() + ".seed.tmp";
        return parent == null ? Path.of(name) : parent.resolve(name);
    }

    /** The total number of descendant nodes under {@code node}, to detect whether a merge added anything. */
    static int nodeCount(org.spongepowered.configurate.ConfigurationNode node) {
        int count = 0;
        for (org.spongepowered.configurate.ConfigurationNode child :
                node.childrenMap().values()) {
            count += 1 + nodeCount(child);
        }
        return count;
    }

    /** Parse a bundled HOCON resource into a node, for use as a defaults tree to merge. */
    static org.spongepowered.configurate.CommentedConfigurationNode parseResource(String resource, ClassLoader loader) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(loader, "loader");
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ConfigException("default resource not found on the classpath: " + resource);
            }
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return org.spongepowered.configurate.hocon.HoconConfigurationLoader.builder()
                    .source(() -> new java.io.BufferedReader(new java.io.StringReader(text)))
                    .build()
                    .load();
        } catch (IOException failure) {
            // ConfigurateException is an IOException subclass, so this single catch covers parse failures too.
            throw new ConfigException("failed to parse default resource " + resource, failure);
        }
    }
}

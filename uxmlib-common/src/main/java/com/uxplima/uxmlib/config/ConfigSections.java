package com.uxplima.uxmlib.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Maps Configurate sections and lists onto typed Java collections, so {@code HoconConfig} can offer
 * {@code getList}/{@code getSection}/{@code keys} without growing past its size cap. A section is a node
 * whose children are named entries (a table); a list is an ordered node.
 *
 * <p>The {@code *Lenient} variants skip a single malformed entry (recording a {@link ConfigViolation} and
 * logging it once) and keep parsing the rest, so one bad row in a config never wipes a whole section.
 */
final class ConfigSections {

    private static final System.Logger LOG = System.getLogger(ConfigSections.class.getName());

    private ConfigSections() {}

    static <T> List<T> list(ConfigurationNode node, String path, Class<T> element) {
        try {
            List<T> value = node.getList(element);
            return value == null ? List.of() : value;
        } catch (SerializationException failure) {
            throw new ConfigException("failed to map list " + path + " to " + element.getName(), failure);
        }
    }

    static <T> Map<String, T> section(ConfigurationNode node, String path, Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        try {
            for (var entry : node.childrenMap().entrySet()) {
                T value = entry.getValue().get(type);
                if (value != null) {
                    result.put(String.valueOf(entry.getKey()), value);
                }
            }
        } catch (SerializationException failure) {
            throw new ConfigException("failed to map section " + path + " to " + type.getName(), failure);
        }
        return result;
    }

    static <T> LenientResult<List<T>> listLenient(ConfigurationNode node, String path, Class<T> element) {
        List<T> kept = new ArrayList<>();
        List<ConfigViolation> skipped = new ArrayList<>();
        List<? extends ConfigurationNode> children = node.childrenList();
        for (int index = 0; index < children.size(); index++) {
            String where = path + "[" + index + "]";
            mapOne(children.get(index), where, element, kept::add, skipped::add);
        }
        return new LenientResult<>(List.copyOf(kept), skipped);
    }

    static <T> LenientResult<Map<String, T>> sectionLenient(ConfigurationNode node, String path, Class<T> type) {
        Map<String, T> kept = new LinkedHashMap<>();
        List<ConfigViolation> skipped = new ArrayList<>();
        for (var entry : node.childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey());
            mapOne(entry.getValue(), path + "." + name, type, value -> kept.put(name, value), skipped::add);
        }
        return new LenientResult<>(kept, skipped);
    }

    private static <T> void mapOne(
            ConfigurationNode child,
            String where,
            Class<T> type,
            java.util.function.Consumer<T> onValue,
            java.util.function.Consumer<ConfigViolation> onSkip) {
        try {
            T value = child.get(type);
            if (value != null) {
                onValue.accept(value);
            }
        } catch (SerializationException failure) {
            // A single bad entry must not abort the whole read: record it, log it once, and move on.
            String reason = java.util.Objects.requireNonNullElse(failure.getMessage(), "malformed entry");
            onSkip.accept(new ConfigViolation(where, reason));
            LOG.log(System.Logger.Level.WARNING, "skipped malformed config entry at " + where, failure);
        }
    }

    static List<String> keys(ConfigurationNode node) {
        List<String> names = new java.util.ArrayList<>();
        for (Object key : node.childrenMap().keySet()) {
            names.add(String.valueOf(key));
        }
        return names;
    }
}

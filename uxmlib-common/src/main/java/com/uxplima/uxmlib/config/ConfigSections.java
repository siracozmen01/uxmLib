package com.uxplima.uxmlib.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Maps Configurate sections and lists onto typed Java collections, so {@code HoconConfig} can offer
 * {@code getList}/{@code getSection}/{@code keys} without growing past its size cap. A section is a node
 * whose children are named entries (a table); a list is an ordered node.
 */
final class ConfigSections {

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

    static List<String> keys(ConfigurationNode node) {
        List<String> names = new java.util.ArrayList<>();
        for (Object key : node.childrenMap().keySet()) {
            names.add(String.valueOf(key));
        }
        return names;
    }
}

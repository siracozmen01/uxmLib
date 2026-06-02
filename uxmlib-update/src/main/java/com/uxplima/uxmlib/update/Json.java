package com.uxplima.uxmlib.update;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The tiny JSON facade the HTTP adapters parse responses with. {@link #parse(String)} returns a plain tree
 * (see {@link JsonReader}); the typed accessors then pull a field out of an object node defensively, treating
 * a missing or wrong-typed field as absent rather than throwing. No JSON dependency, no substring-scan.
 */
final class Json {

    private Json() {}

    /** Parse a complete JSON document into a plain tree. Throws on malformed or trailing input. */
    static @Nullable Object parse(String text) {
        Objects.requireNonNull(text, "text");
        return new JsonReader(text).parse();
    }

    /** The string field {@code key} of {@code node}, or empty if absent or not a string. */
    static Optional<String> string(@Nullable Object node, String key) {
        Objects.requireNonNull(key, "key");
        if (node instanceof Map<?, ?> map && map.get(key) instanceof String value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /** The object field {@code key} of {@code node}, or empty if absent or not an object. */
    static Optional<Map<?, ?>> object(@Nullable Object node, String key) {
        Objects.requireNonNull(key, "key");
        if (node instanceof Map<?, ?> map && map.get(key) instanceof Map<?, ?> value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    /** The array field {@code key} of {@code node}, or empty if absent or not an array. */
    static Optional<List<?>> array(@Nullable Object node, String key) {
        Objects.requireNonNull(key, "key");
        if (node instanceof Map<?, ?> map && map.get(key) instanceof List<?> value) {
            return Optional.of(value);
        }
        return Optional.empty();
    }
}

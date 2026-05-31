package com.uxplima.uxmlib.config;

import java.util.Locale;
import java.util.function.Predicate;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

/**
 * Configurate scalar serializers for the common Bukkit value types, so config can map a string straight
 * onto a {@link Material}, {@link NamespacedKey}, or {@link Color} (and the reverse) without per-field
 * parsing. Apply them with {@link HoconConfig#load(java.nio.file.Path, TypeSerializerCollection)} using
 * {@link #bukkit()}.
 *
 * <p>{@link ScalarSerializer#of} takes a serialize function {@code (value, typeTest) -> serializedForm}
 * and a deserialize function {@code raw -> value}. The serialize lambda's parameters are typed
 * explicitly because, with both lambdas present, javac otherwise infers the value parameter as
 * {@code Object}; the type-test predicate is unused since each value renders to a single string.
 */
public final class ConfigCodecs {

    private ConfigCodecs() {}

    /** The default serializers plus uxmlib's Bukkit-type scalars (Material, NamespacedKey, Color). */
    public static TypeSerializerCollection bukkit() {
        return TypeSerializerCollection.defaults()
                .childBuilder()
                .register(Material.class, materialSerializer())
                .register(NamespacedKey.class, namespacedKeySerializer())
                .register(Color.class, colorSerializer())
                .build();
    }

    private static ScalarSerializer<Material> materialSerializer() {
        return ScalarSerializer.of(
                Material.class,
                (Material material, Predicate<Class<?>> typeTest) -> material.name(),
                raw -> parseMaterial(raw.toString()));
    }

    private static Material parseMaterial(String raw) throws SerializationException {
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw new SerializationException("unknown material: " + raw);
        }
        return material;
    }

    private static ScalarSerializer<NamespacedKey> namespacedKeySerializer() {
        return ScalarSerializer.of(
                NamespacedKey.class,
                (NamespacedKey key, Predicate<Class<?>> typeTest) -> key.asString(),
                raw -> parseKey(raw.toString()));
    }

    private static NamespacedKey parseKey(String raw) throws SerializationException {
        NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        if (key == null) {
            throw new SerializationException("invalid namespaced key: " + raw);
        }
        return key;
    }

    private static ScalarSerializer<Color> colorSerializer() {
        // Stored as a #RRGGBB hex string.
        return ScalarSerializer.of(
                Color.class,
                (Color color, Predicate<Class<?>> typeTest) -> String.format(Locale.ROOT, "#%06X", color.asRGB()),
                raw -> parseColor(raw.toString()));
    }

    private static Color parseColor(String raw) throws SerializationException {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            // NumberFormatException is a subclass of IllegalArgumentException, so one catch covers both
            // bad hex and an out-of-range RGB value.
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (IllegalArgumentException failure) {
            throw new SerializationException("invalid colour: " + raw);
        }
    }
}

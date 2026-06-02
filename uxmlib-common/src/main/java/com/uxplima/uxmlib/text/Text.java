package com.uxplima.uxmlib.text;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage convenience API. A single shared {@link MiniMessage} instance parses strings into
 * {@link Component}s, with helpers for the common placeholder shapes and for flattening a component back
 * to plain text. All user-visible text in a plugin should flow through here rather than legacy colour codes.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {}

    /** Parse a MiniMessage string into a component. */
    public static Component mini(String input) {
        Objects.requireNonNull(input, "input");
        return MINI.deserialize(input);
    }

    /** Parse a MiniMessage string, resolving the supplied tags/placeholders. */
    public static Component mini(String input, TagResolver... resolvers) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(resolvers, "resolvers");
        return MINI.deserialize(input, resolvers);
    }

    /**
     * A {@code <key>} placeholder whose value is inserted literally — any MiniMessage tags in the value
     * are shown as text, never parsed. This is the safe default for untrusted or user-provided values.
     */
    public static TagResolver placeholder(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return Placeholder.unparsed(key, value);
    }

    /** A {@code <key>} placeholder whose value is itself parsed as MiniMessage. */
    public static TagResolver parsed(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return Placeholder.parsed(key, value);
    }

    /** A {@code <key>} placeholder that inserts an already-built component. */
    public static TagResolver component(String key, Component value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        return Placeholder.component(key, value);
    }

    /** Flatten a component to plain text, dropping all formatting. */
    public static String plain(Component component) {
        Objects.requireNonNull(component, "component");
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Serialize a component back to a MiniMessage string, the inverse of {@link #mini(String)}. Used to
     * write a styled component back to a config losslessly so it can be re-parsed on reload.
     */
    public static String serialize(Component component) {
        Objects.requireNonNull(component, "component");
        return MINI.serialize(component);
    }

    /** Strip every MiniMessage tag from a string, leaving its literal text. */
    public static String stripTags(String input) {
        Objects.requireNonNull(input, "input");
        return MINI.stripTags(input);
    }
}

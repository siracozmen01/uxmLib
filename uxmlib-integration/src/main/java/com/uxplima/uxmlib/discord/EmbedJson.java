package com.uxplima.uxmlib.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes a {@link DiscordEmbed} to its Discord JSON object. Only set fields are emitted, so a minimal
 * embed stays small and a full one carries author/footer/thumbnail/image/timestamp/fields. Strings are
 * escaped through {@link DiscordWebhook#jsonString}. Kept apart from the webhook so the encoding has one
 * home and each helper stays short.
 */
final class EmbedJson {

    private EmbedJson() {}

    /** Encodes a list of embeds to the JSON array Discord expects under the {@code "embeds"} key. */
    static String encodeArray(List<DiscordEmbed> embeds) {
        List<String> encoded = new ArrayList<>(embeds.size());
        for (DiscordEmbed embed : embeds) {
            encoded.add(encode(embed));
        }
        return "[" + String.join(",", encoded) + "]";
    }

    static String encode(DiscordEmbed embed) {
        List<String> parts = new ArrayList<>();
        add(parts, "title", embed.title());
        add(parts, "description", embed.description());
        add(parts, "url", embed.url());
        if (embed.color() != null) {
            parts.add("\"color\":" + embed.color());
        }
        if (embed.timestamp() != null) {
            parts.add("\"timestamp\":"
                    + DiscordWebhook.jsonString(embed.timestamp().toString()));
        }
        addAuthor(parts, embed.author());
        addFooter(parts, embed.footer());
        addImage(parts, "thumbnail", embed.thumbnailUrl());
        addImage(parts, "image", embed.imageUrl());
        addFields(parts, embed.fields());
        return "{" + String.join(",", parts) + "}";
    }

    private static void add(List<String> parts, String key, @org.jspecify.annotations.Nullable String value) {
        if (value != null) {
            parts.add("\"" + key + "\":" + DiscordWebhook.jsonString(value));
        }
    }

    private static void addAuthor(List<String> parts, DiscordEmbed.@org.jspecify.annotations.Nullable Author author) {
        if (author == null) {
            return;
        }
        List<String> inner = new ArrayList<>();
        add(inner, "name", author.name());
        add(inner, "url", author.url());
        add(inner, "icon_url", author.iconUrl());
        parts.add("\"author\":{" + String.join(",", inner) + "}");
    }

    private static void addFooter(List<String> parts, DiscordEmbed.@org.jspecify.annotations.Nullable Footer footer) {
        if (footer == null) {
            return;
        }
        List<String> inner = new ArrayList<>();
        add(inner, "text", footer.text());
        add(inner, "icon_url", footer.iconUrl());
        parts.add("\"footer\":{" + String.join(",", inner) + "}");
    }

    private static void addImage(List<String> parts, String key, @org.jspecify.annotations.Nullable String url) {
        if (url != null) {
            parts.add("\"" + key + "\":{\"url\":" + DiscordWebhook.jsonString(url) + "}");
        }
    }

    private static void addFields(List<String> parts, List<DiscordEmbed.Field> fields) {
        if (fields.isEmpty()) {
            return;
        }
        List<String> encoded = new ArrayList<>(fields.size());
        for (DiscordEmbed.Field field : fields) {
            encoded.add("{\"name\":" + DiscordWebhook.jsonString(field.name()) + ",\"value\":"
                    + DiscordWebhook.jsonString(field.value()) + ",\"inline\":" + field.inline() + "}");
        }
        parts.add("\"fields\":[" + String.join(",", encoded) + "]");
    }
}

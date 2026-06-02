package com.uxplima.uxmlib.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The canonical payload for one webhook POST: an optional content line, up to ten embeds, and optional
 * identity overrides ({@code username}, {@code avatar_url}) plus a {@code thread_name} for posting into a
 * forum/thread. Built through {@link #builder()}; {@link Builder#build()} enforces the documented Discord
 * limits and rejects a wholly-blank message, so the failure is a clear {@link IllegalArgumentException} at
 * call time rather than a 400 from Discord. Encoded to JSON by {@link #body()}.
 *
 * <p>The {@code content}/{@code username}/{@code avatar_url}/{@code thread_name}/{@code embeds} field shapes
 * follow the Discord webhook JSON; the transport, escaping, and validation are this library's own.
 */
public record WebhookMessage(
        @Nullable String content,
        List<DiscordEmbed> embeds,
        @Nullable String username,
        @Nullable String avatarUrl,
        @Nullable String threadName) {

    /** Discord caps content at 2000 characters and a single message at 10 embeds. */
    static final int CONTENT_MAX = 2000;

    static final int EMBEDS_MAX = 10;

    public WebhookMessage {
        embeds = List.copyOf(embeds);
    }

    /** Start building a webhook message. */
    public static Builder builder() {
        return new Builder();
    }

    /** The full JSON request body for this message, mentions suppressed. */
    public String body() {
        List<String> parts = new ArrayList<>();
        if (content != null) {
            parts.add("\"content\":" + DiscordWebhook.jsonString(content));
        }
        if (!embeds.isEmpty()) {
            parts.add("\"embeds\":" + EmbedJson.encodeArray(embeds));
        }
        addOverride(parts, "username", username);
        addOverride(parts, "avatar_url", avatarUrl);
        addOverride(parts, "thread_name", threadName);
        parts.add(DiscordWebhook.ALLOWED_MENTIONS_NONE);
        return "{" + String.join(",", parts) + "}";
    }

    private static void addOverride(List<String> parts, String key, @Nullable String value) {
        if (value != null) {
            parts.add("\"" + key + "\":" + DiscordWebhook.jsonString(value));
        }
    }

    /** Fluent builder; {@link #build()} validates and is the only way to obtain a message. */
    public static final class Builder {
        private @Nullable String content;
        private final List<DiscordEmbed> embeds = new ArrayList<>();
        private @Nullable String username;
        private @Nullable String avatarUrl;
        private @Nullable String threadName;

        private Builder() {}

        public Builder content(String value) {
            this.content = Objects.requireNonNull(value, "content");
            return this;
        }

        public Builder embed(DiscordEmbed value) {
            this.embeds.add(Objects.requireNonNull(value, "embed"));
            return this;
        }

        public Builder embeds(List<DiscordEmbed> values) {
            Objects.requireNonNull(values, "embeds");
            for (DiscordEmbed value : values) {
                embed(value);
            }
            return this;
        }

        public Builder username(String value) {
            this.username = Objects.requireNonNull(value, "username");
            return this;
        }

        public Builder avatarUrl(String value) {
            this.avatarUrl = Objects.requireNonNull(value, "avatarUrl");
            return this;
        }

        public Builder threadName(String value) {
            this.threadName = Objects.requireNonNull(value, "threadName");
            return this;
        }

        /** Validate the documented limits and build, or throw {@link IllegalArgumentException} listing each. */
        public WebhookMessage build() {
            List<String> violations = validate();
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("invalid webhook message: " + String.join("; ", violations));
            }
            return new WebhookMessage(content, embeds, username, avatarUrl, threadName);
        }

        private List<String> validate() {
            List<String> violations = new ArrayList<>();
            boolean hasContent = content != null && !content.isBlank();
            if (!hasContent && embeds.isEmpty()) {
                violations.add("message is empty (no content and no embeds)");
            }
            if (content != null && content.length() > CONTENT_MAX) {
                violations.add("content exceeds " + CONTENT_MAX + " characters");
            }
            if (embeds.size() > EMBEDS_MAX) {
                violations.add("message has more than " + EMBEDS_MAX + " embeds");
            }
            for (DiscordEmbed embed : embeds) {
                violations.addAll(EmbedLimits.violations(embed));
            }
            return violations;
        }
    }
}

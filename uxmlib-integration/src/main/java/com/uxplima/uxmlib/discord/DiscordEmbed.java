package com.uxplima.uxmlib.discord;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A Discord embed. Title, description, and colour cover the common notification case via {@link #of} and
 * {@link #colored}; the full surface — fields, author, footer, thumbnail, image, timestamp, URL — is
 * available through {@link #builder()}. Encoded to JSON by {@link DiscordWebhook}.
 */
public record DiscordEmbed(
        @Nullable String title,
        @Nullable String description,
        @Nullable Integer color,
        @Nullable String url,
        @Nullable Author author,
        @Nullable Footer footer,
        @Nullable String thumbnailUrl,
        @Nullable String imageUrl,
        @Nullable Instant timestamp,
        List<Field> fields) {

    public DiscordEmbed {
        fields = List.copyOf(fields);
    }

    /** An author line: a name, an optional link, and an optional icon. */
    public record Author(String name, @Nullable String url, @Nullable String iconUrl) {
        public Author {
            Objects.requireNonNull(name, "name");
        }
    }

    /** A footer: text plus an optional icon. */
    public record Footer(String text, @Nullable String iconUrl) {
        public Footer {
            Objects.requireNonNull(text, "text");
        }
    }

    /** One field: a name, a value, and whether it renders inline with adjacent fields. */
    public record Field(String name, String value, boolean inline) {
        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    /** An embed with a title and description and no colour. */
    public static DiscordEmbed of(String title, String description) {
        return builder().title(title).description(description).build();
    }

    /** An embed with a title, description, and colour bar ({@code 0xRRGGBB}). */
    public static DiscordEmbed colored(String title, String description, int color) {
        return builder().title(title).description(description).color(color).build();
    }

    /** Start building a full embed. */
    public static Builder builder() {
        return new Builder();
    }

    /** The colour as an optional. */
    public Optional<Integer> colorValue() {
        return Optional.ofNullable(color);
    }

    /** Fluent builder for the full embed surface. */
    public static final class Builder {
        private @Nullable String title;
        private @Nullable String description;
        private @Nullable Integer color;
        private @Nullable String url;
        private @Nullable Author author;
        private @Nullable Footer footer;
        private @Nullable String thumbnailUrl;
        private @Nullable String imageUrl;
        private @Nullable Instant timestamp;
        private final java.util.List<Field> fields = new java.util.ArrayList<>();

        private Builder() {}

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder color(int value) {
            this.color = value;
            return this;
        }

        public Builder url(String value) {
            this.url = value;
            return this;
        }

        public Builder author(String name, @Nullable String url, @Nullable String iconUrl) {
            this.author = new Author(name, url, iconUrl);
            return this;
        }

        public Builder footer(String text, @Nullable String iconUrl) {
            this.footer = new Footer(text, iconUrl);
            return this;
        }

        public Builder thumbnail(String url) {
            this.thumbnailUrl = value(url);
            return this;
        }

        public Builder image(String url) {
            this.imageUrl = value(url);
            return this;
        }

        public Builder timestamp(Instant value) {
            this.timestamp = value;
            return this;
        }

        public Builder field(String name, String value, boolean inline) {
            this.fields.add(new Field(name, value, inline));
            return this;
        }

        public DiscordEmbed build() {
            return new DiscordEmbed(
                    title, description, color, url, author, footer, thumbnailUrl, imageUrl, timestamp, fields);
        }

        private static String value(String v) {
            return Objects.requireNonNull(v, "url");
        }
    }
}

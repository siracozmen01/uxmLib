package com.uxplima.uxmlib.discord;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import net.kyori.adventure.util.RGBLike;

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

        /** Fluent builder for an author line, used by the closure-style {@code author(...)} on the embed. */
        public static final class Builder {
            private @Nullable String name;
            private @Nullable String url;
            private @Nullable String iconUrl;

            private Builder() {}

            public Builder name(String value) {
                this.name = Objects.requireNonNull(value, "name");
                return this;
            }

            public Builder url(@Nullable String value) {
                this.url = value;
                return this;
            }

            public Builder iconUrl(@Nullable String value) {
                this.iconUrl = value;
                return this;
            }

            Author build() {
                return new Author(Objects.requireNonNull(name, "name"), url, iconUrl);
            }
        }
    }

    /** A footer: text plus an optional icon. */
    public record Footer(String text, @Nullable String iconUrl) {
        public Footer {
            Objects.requireNonNull(text, "text");
        }

        /** Fluent builder for a footer, used by the closure-style {@code footer(...)} on the embed. */
        public static final class Builder {
            private @Nullable String text;
            private @Nullable String iconUrl;

            private Builder() {}

            public Builder text(String value) {
                this.text = Objects.requireNonNull(value, "text");
                return this;
            }

            public Builder iconUrl(@Nullable String value) {
                this.iconUrl = value;
                return this;
            }

            Footer build() {
                return new Footer(Objects.requireNonNull(text, "text"), iconUrl);
            }
        }
    }

    /** One field: a name, a value, and whether it renders inline with adjacent fields. */
    public record Field(String name, String value, boolean inline) {
        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }

        /** Fluent builder for a field, used by the closure-style {@code field(...)} on the embed. */
        public static final class Builder {
            private @Nullable String name;
            private @Nullable String value;
            private boolean inline;

            private Builder() {}

            public Builder name(String value) {
                this.name = Objects.requireNonNull(value, "name");
                return this;
            }

            public Builder value(String value) {
                this.value = Objects.requireNonNull(value, "value");
                return this;
            }

            public Builder inline(boolean value) {
                this.inline = value;
                return this;
            }

            Field build() {
                return new Field(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"), inline);
            }
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

        /** A multi-line description, each argument becoming its own line (joined with {@code "\n"}). */
        public Builder description(String... lines) {
            return description(List.of(Objects.requireNonNull(lines, "lines")));
        }

        /** A multi-line description from a list, each element becoming its own line (joined with {@code "\n"}). */
        public Builder description(List<String> lines) {
            Objects.requireNonNull(lines, "lines");
            this.description = String.join("\n", lines);
            return this;
        }

        public Builder color(int value) {
            this.color = value;
            return this;
        }

        /** The colour bar from an Adventure colour (or any {@link RGBLike}); packed to {@code 0xRRGGBB}. */
        public Builder color(RGBLike rgb) {
            Objects.requireNonNull(rgb, "rgb");
            return color(rgb.red(), rgb.green(), rgb.blue());
        }

        /** The colour bar from 0-255 red/green/blue components, packed to a single {@code 0xRRGGBB} integer. */
        public Builder color(int red, int green, int blue) {
            this.color = (channel(red, "red") << 16) | (channel(green, "green") << 8) | channel(blue, "blue");
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

        /** The author line, configured through a nested {@link Author.Builder} closure. */
        public Builder author(UnaryOperator<Author.Builder> spec) {
            Objects.requireNonNull(spec, "spec");
            this.author = spec.apply(new Author.Builder()).build();
            return this;
        }

        public Builder footer(String text, @Nullable String iconUrl) {
            this.footer = new Footer(text, iconUrl);
            return this;
        }

        /** The footer, configured through a nested {@link Footer.Builder} closure. */
        public Builder footer(UnaryOperator<Footer.Builder> spec) {
            Objects.requireNonNull(spec, "spec");
            this.footer = spec.apply(new Footer.Builder()).build();
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

        /** Adds one field, configured through a nested {@link Field.Builder} closure. */
        public Builder field(UnaryOperator<Field.Builder> spec) {
            Objects.requireNonNull(spec, "spec");
            this.fields.add(spec.apply(new Field.Builder()).build());
            return this;
        }

        public DiscordEmbed build() {
            return new DiscordEmbed(
                    title, description, color, url, author, footer, thumbnailUrl, imageUrl, timestamp, fields);
        }

        private static String value(String v) {
            return Objects.requireNonNull(v, "url");
        }

        /** A single 0-255 colour channel, rejected outside that range so a bad component fails fast. */
        private static int channel(int value, String name) {
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException(name + " must be in 0..255: " + value);
            }
            return value;
        }
    }
}

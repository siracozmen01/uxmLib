package com.uxplima.uxmlib.discord;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A minimal Discord embed: a title, a description, and an optional colour (the left bar, as a 0xRRGGBB
 * integer). Build one with the static factories and send it with {@link DiscordWebhook#sendEmbed}. This
 * is deliberately small — Discord embeds support far more (fields, author, footer, images), but title +
 * description + colour covers notification use; extend later if needed.
 */
public record DiscordEmbed(String title, String description, @Nullable Integer color) {

    public DiscordEmbed {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }

    /** An embed with a title and description and no colour. */
    public static DiscordEmbed of(String title, String description) {
        return new DiscordEmbed(title, description, null);
    }

    /** An embed with a title, description, and colour bar ({@code 0xRRGGBB}). */
    public static DiscordEmbed colored(String title, String description, int color) {
        return new DiscordEmbed(title, description, color);
    }

    /** The colour as an optional. */
    public Optional<Integer> colorValue() {
        return Optional.ofNullable(color);
    }
}

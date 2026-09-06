package com.uxplima.uxmlib.text.language;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * What a server decides about language, read from a plugin's own configuration.
 *
 * @param defaultLocale the language a viewer reads when nothing else answers. Never {@code null}, because a
 *     message with no language is a message nobody can read.
 * @param followClient whether the client's own language is used when the player has not chosen one. An
 *     operator who turns this off starts every player in {@code defaultLocale} and lets them change it,
 *     which is what a server with one audience wants.
 * @param forced a language the server imposes on every viewer, or {@code null} for none. It beats a player's
 *     own choice, so it is the setting a single-language server uses and nobody else touches.
 */
public record LanguageSettings(
        Locale defaultLocale,
        boolean followClient,
        @Nullable Locale forced) {

    public LanguageSettings {
        Objects.requireNonNull(defaultLocale, "defaultLocale");
    }

    /** The common case: follow the client, force nothing. */
    public static LanguageSettings following(Locale defaultLocale) {
        return new LanguageSettings(defaultLocale, true, null);
    }

    /** The language the server imposes, if it imposes one. */
    public Optional<Locale> forcedLocale() {
        return Optional.ofNullable(forced);
    }
}

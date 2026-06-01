package com.uxplima.uxmlib.text.message;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

/**
 * Resolves the locale to render a message in for a given viewer, with a server-configured default. A
 * {@link Player}'s locale comes from Adventure's native {@link Player#locale()} (the client's language
 * setting, no NMS); any other audience — console, a broadcast group — falls back to the default.
 *
 * <p>The interface is the seam the catalog depends on, so tests inject a fake that maps a viewer to a fixed
 * locale without a live server. {@link #ofDefault(Locale)} is the production implementation.
 */
public interface LocaleSource {

    /** The server default locale, used for any viewer whose own locale cannot be determined. */
    Locale defaultLocale();

    /** The locale to render for {@code viewer}, never {@code null}; falls back to {@link #defaultLocale()}. */
    Locale localeOf(Audience viewer);

    /**
     * The production source: a {@link Player} contributes its own {@link Player#locale()}, everything else
     * uses {@code defaultLocale}.
     */
    static LocaleSource ofDefault(Locale defaultLocale) {
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        return new PlayerLocaleSource(defaultLocale);
    }

    /** Resolves a {@link Player}'s client locale and otherwise yields the configured default. */
    record PlayerLocaleSource(Locale defaultLocale) implements LocaleSource {

        public PlayerLocaleSource {
            Objects.requireNonNull(defaultLocale, "defaultLocale");
        }

        @Override
        public Locale localeOf(Audience viewer) {
            Objects.requireNonNull(viewer, "viewer");
            if (viewer instanceof Player player) {
                return player.locale();
            }
            return defaultLocale;
        }
    }
}

package com.uxplima.uxmlib.text.message;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Resolves a {@link MessageKey} to a MiniMessage template for a target locale, with the three-tier fallback
 * that defines the i18n layer:
 *
 * <ol>
 *   <li>the template for the viewer's exact locale, else its language-only locale ({@code en_US} &rarr;
 *       {@code en});
 *   <li>the same lookup against the configured default locale;
 *   <li>the key's own {@link MessageKey#defaultTemplate()}.
 * </ol>
 *
 * <p>The catalog is pure: it holds an immutable per-locale map of {@code path -> template} and a default
 * locale, so its fallback logic is unit-testable with no server. Loading those maps from one HOCON file per
 * locale lives in {@link MessageCatalogLoader}, which keeps this class within its size cap.
 */
public final class MessageCatalog {

    private final Map<Locale, Map<String, String>> templatesByLocale;
    private final Locale defaultLocale;

    /**
     * @param templatesByLocale a map from locale to that locale's {@code path -> template} entries; copied
     *     defensively, so later mutation of the argument cannot change the catalog
     * @param defaultLocale the locale tried after the viewer's own, before a key's built-in default
     */
    public MessageCatalog(Map<Locale, Map<String, String>> templatesByLocale, Locale defaultLocale) {
        Objects.requireNonNull(templatesByLocale, "templatesByLocale");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        this.templatesByLocale = copy(templatesByLocale);
        this.defaultLocale = defaultLocale;
    }

    private static Map<Locale, Map<String, String>> copy(Map<Locale, Map<String, String>> source) {
        Map<Locale, Map<String, String>> result = new HashMap<>();
        for (var entry : source.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "locale");
            result.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    /** The locale tried after the viewer's own. */
    public Locale defaultLocale() {
        return defaultLocale;
    }

    /**
     * The template for {@code key} rendered to {@code locale}, applying the three-tier fallback. Always
     * returns text: the key's built-in default is the last resort, so a caller never gets an empty string.
     */
    public String template(MessageKey key, Locale locale) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(locale, "locale");
        String fromLocale = lookup(key.path(), locale);
        if (fromLocale != null) {
            return fromLocale;
        }
        String fromDefault = lookup(key.path(), defaultLocale);
        if (fromDefault != null) {
            return fromDefault;
        }
        return key.defaultTemplate();
    }

    /** Whether any lang file (viewer or default locale) supplies a template for {@code key}. */
    public boolean isTranslated(MessageKey key, Locale locale) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(locale, "locale");
        return lookup(key.path(), locale) != null || lookup(key.path(), defaultLocale) != null;
    }

    // Exact locale first, then a language-only locale, so a translator can ship one en.conf and still serve
    // en_US, en_GB, and so on without duplicating every region.
    private @Nullable String lookup(String path, Locale locale) {
        String exact = templateAt(locale, path);
        if (exact != null) {
            return exact;
        }
        if (!locale.getCountry().isEmpty()) {
            return templateAt(Locale.of(locale.getLanguage()), path);
        }
        return null;
    }

    private @Nullable String templateAt(Locale locale, String path) {
        Map<String, String> templates = templatesByLocale.get(locale);
        return templates == null ? null : templates.get(path);
    }

    /**
     * Collect the built-in defaults of {@code keys} into a {@code path -> template} map, so an operator can be
     * handed a complete starter lang file rather than discovering paths by trial and error.
     */
    public static Map<String, String> defaults(Iterable<? extends MessageKey> keys) {
        Objects.requireNonNull(keys, "keys");
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (MessageKey key : keys) {
            Objects.requireNonNull(key, "key");
            result.put(key.path(), key.defaultTemplate());
        }
        return result;
    }

    /** The starter defaults for a {@link MessageKey} enum, the common declaration shape. */
    public static <E extends Enum<E> & MessageKey> Map<String, String> defaults(Class<E> keyEnum) {
        Objects.requireNonNull(keyEnum, "keyEnum");
        return defaults(Optional.of(keyEnum)
                .map(Class::getEnumConstants)
                .map(java.util.List::of)
                .orElseThrow());
    }
}

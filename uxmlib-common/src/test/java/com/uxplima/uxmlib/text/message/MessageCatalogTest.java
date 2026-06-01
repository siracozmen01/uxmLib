package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The pure three-tier fallback: viewer locale, then default locale, then the key's built-in default. */
class MessageCatalogTest {

    private static final MessageKey WELCOME = MessageKey.of("join.welcome", "<green>Welcome");

    private static final Locale GERMAN = Locale.GERMAN;
    private static final Locale GERMANY = Locale.of("de", "DE");

    private MessageCatalog catalog(Map<Locale, Map<String, String>> templates) {
        return new MessageCatalog(templates, Locale.ENGLISH);
    }

    @Test
    void firstTierUsesTheViewerLocaleTemplate() {
        MessageCatalog catalog =
                catalog(Map.of(GERMAN, Map.of("join.welcome", "<green>Willkommen"), Locale.ENGLISH, Map.of()));

        assertThat(catalog.template(WELCOME, GERMAN)).isEqualTo("<green>Willkommen");
    }

    @Test
    void secondTierFallsBackToTheDefaultLocale() {
        MessageCatalog catalog = catalog(Map.of(Locale.ENGLISH, Map.of("join.welcome", "<green>Welcome, friend")));

        assertThat(catalog.template(WELCOME, GERMAN)).isEqualTo("<green>Welcome, friend");
    }

    @Test
    void thirdTierFallsBackToTheKeyBuiltInDefault() {
        MessageCatalog catalog = catalog(Map.of(Locale.ENGLISH, Map.of()));

        assertThat(catalog.template(WELCOME, GERMAN)).isEqualTo("<green>Welcome");
    }

    @Test
    void aRegionLocaleFallsBackToItsLanguageOnlyFile() {
        MessageCatalog catalog = catalog(Map.of(GERMAN, Map.of("join.welcome", "<green>Willkommen")));

        // de_DE is not present, but de is — the region viewer should still get the German text.
        assertThat(catalog.template(WELCOME, GERMANY)).isEqualTo("<green>Willkommen");
    }

    @Test
    void isTranslatedReflectsWhetherAnyFileSuppliesTheKey() {
        MessageCatalog translated = catalog(Map.of(GERMAN, Map.of("join.welcome", "x")));
        MessageCatalog untranslated = catalog(Map.of(Locale.ENGLISH, Map.of()));

        assertThat(translated.isTranslated(WELCOME, GERMAN)).isTrue();
        assertThat(untranslated.isTranslated(WELCOME, GERMAN)).isFalse();
    }

    @Test
    void mutatingTheSourceMapAfterConstructionDoesNotChangeTheCatalog() {
        var mutable = new java.util.HashMap<Locale, Map<String, String>>();
        mutable.put(GERMAN, new java.util.HashMap<>(Map.of("join.welcome", "<green>Willkommen")));
        MessageCatalog catalog = new MessageCatalog(mutable, Locale.ENGLISH);

        mutable.clear();

        assertThat(catalog.template(WELCOME, GERMAN)).isEqualTo("<green>Willkommen");
    }

    enum Keys implements MessageKey {
        HELLO("greet.hello", "<yellow>Hi"),
        BYE("greet.bye", "<gray>Bye");

        private final String path;
        private final String def;

        Keys(String path, String def) {
            this.path = path;
            this.def = def;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public String defaultTemplate() {
            return def;
        }
    }

    @Test
    void defaultsHarvestEveryEnumKeyForAStarterFile() {
        Map<String, String> defaults = MessageCatalog.defaults(Keys.class);

        assertThat(defaults)
                .containsExactly(Map.entry("greet.hello", "<yellow>Hi"), Map.entry("greet.bye", "<gray>Bye"));
    }
}

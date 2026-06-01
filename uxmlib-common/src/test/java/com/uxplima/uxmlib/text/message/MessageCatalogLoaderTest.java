package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The loader flattens a nested HOCON lang file into the dotted {@code path -> template} map. */
class MessageCatalogLoaderTest {

    private static final MessageKey WELCOME = MessageKey.of("join.welcome", "<green>fallback");
    private static final MessageKey BYE = MessageKey.of("quit.bye", "<gray>fallback");

    @Test
    void flattensNestedSectionsIntoDottedPaths(@TempDir Path dir) throws Exception {
        Path en = dir.resolve("en.conf");
        Files.writeString(en, "join { welcome = \"<green>Welcome\" }\nquit { bye = \"<gray>Bye\" }\n");
        HoconConfig config = HoconConfig.load(en);

        MessageCatalog catalog = MessageCatalogLoader.fromNodes(Map.of(Locale.ENGLISH, config.root()), Locale.ENGLISH);

        assertThat(catalog.template(WELCOME, Locale.ENGLISH)).isEqualTo("<green>Welcome");
        assertThat(catalog.template(BYE, Locale.ENGLISH)).isEqualTo("<gray>Bye");
    }

    @Test
    void perLocaleFilesProduceIndependentTemplates(@TempDir Path dir) throws Exception {
        Path en = dir.resolve("en.conf");
        Path de = dir.resolve("de.conf");
        Files.writeString(en, "join { welcome = \"<green>Welcome\" }\n");
        Files.writeString(de, "join { welcome = \"<green>Willkommen\" }\n");

        MessageCatalog catalog = MessageCatalogLoader.fromNodes(
                Map.of(
                        Locale.ENGLISH, HoconConfig.load(en).root(),
                        Locale.GERMAN, HoconConfig.load(de).root()),
                Locale.ENGLISH);

        assertThat(catalog.template(WELCOME, Locale.GERMAN)).isEqualTo("<green>Willkommen");
        assertThat(catalog.template(WELCOME, Locale.ENGLISH)).isEqualTo("<green>Welcome");
    }

    @Test
    void anAbsentKeyFallsBackToTheBuiltInDefault(@TempDir Path dir) throws Exception {
        Path en = dir.resolve("en.conf");
        Files.writeString(en, "join { welcome = \"<green>Welcome\" }\n");

        MessageCatalog catalog = MessageCatalogLoader.fromNodes(
                Map.of(Locale.ENGLISH, HoconConfig.load(en).root()), Locale.ENGLISH);

        assertThat(catalog.template(BYE, Locale.ENGLISH)).isEqualTo("<gray>fallback");
    }
}

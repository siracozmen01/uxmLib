package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.Test;

/**
 * Covers the command layer reading its own lines out of a consumer's catalog: a translated line is used,
 * a line the file does not hold falls back to the key's default, and whatever the sender typed is shown
 * rather than parsed.
 */
class CatalogueCommandMessagesTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    @Test
    void aTranslatedLineComesOutInThatLanguage() {
        CommandMessages messages =
                messagesHolding(Map.of(TR, Map.of("command.player-only", "Bunu sadece bir oyuncu yazabilir.")));

        assertThat(Text.plain(messages.playerOnly(TR))).isEqualTo("Bunu sadece bir oyuncu yazabilir.");
    }

    @Test
    void aLineTheFileDoesNotHoldFallsBackToTheKeyDefault() {
        CommandMessages messages = messagesHolding(Map.of());

        assertThat(Text.plain(messages.playerOnly(Locale.ENGLISH))).isEqualTo("Only a player can run this command.");
    }

    /**
     * A shipped default has to read correctly with no style layer wired at all, because a consumer may take
     * this module on its own. MiniMessage leaves a tag it does not know as literal text, so a default written
     * in a style layer's vocabulary reaches a player as the characters of the tag instead of as a sentence.
     * For the plugin that owns the style layer that is a colour it did not want; for everybody else it is a
     * defect. This fails the day a token is written back into one.
     */
    @Test
    void noShippedDefaultNamesATagOnlyAStyleLayerResolves() {
        assertThat(CommandLine.values()).allSatisfy(line -> assertThat(line.defaultTemplate())
                .describedAs(line.path())
                .doesNotContain("<tag:", "<etag:", "<h:", "<g:", "<plain>", "<caps>")
                .doesNotContain("<body>", "<accent>", "<value>", "<subtext>", "<dim>", "<muted>", "<good>", "<bad>"));
    }

    @Test
    void whatTheSenderTypedIsShownAndNeverObeyed() {
        CommandMessages messages =
                messagesHolding(Map.of(Locale.ENGLISH, Map.of("command.invalid-value", "no: <input> for <argument>")));

        String shown = Text.plain(messages.invalidValue(Locale.ENGLISH, "player", "<red>gotcha</red>", ""));

        assertThat(shown).isEqualTo("no: <red>gotcha</red> for player");
    }

    @Test
    void aListOfAllowedValuesIsJoinedForTheReader() {
        CommandMessages messages =
                messagesHolding(Map.of(Locale.ENGLISH, Map.of("command.not-one-of", "<input>: try <allowed>")));

        String shown = Text.plain(messages.notOneOf(Locale.ENGLISH, "mode", "spin", List.of("on", "off")));

        assertThat(shown).isEqualTo("spin: try on, off");
    }

    @Test
    void everyLineTheCommandLayerWritesIsAnsweredFromTheCatalogue() {
        // A method left at its default is a line a player reads in English, in vanilla colours, from a jar
        // that no style pass over a plugin's own resources can reach. This fails the day a line is added
        // here without a key to word it.
        List<String> written = Arrays.stream(CommandMessages.class.getDeclaredMethods())
                .filter(Method::isDefault)
                .map(Method::getName)
                .sorted()
                .toList();
        List<String> answered = Arrays.stream(CatalogueCommandMessages.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(answered).containsAll(written);
    }

    private static CommandMessages messagesHolding(Map<Locale, Map<String, String>> files) {
        MessageCatalog catalog = new MessageCatalog(files, Locale.ENGLISH);
        return CommandMessages.fromCatalogue(new Messages(catalog, LocaleSource.ofDefault(Locale.ENGLISH)));
    }
}

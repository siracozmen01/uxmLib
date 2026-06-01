package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Ties the catalog, locale source and delivery together: a player's own locale drives which template is
 * rendered, the supplied placeholders are substituted, and the per-key channel routes the send.
 */
class MessagesTest {

    private static final MessageKey WELCOME = MessageKey.of("join.welcome", "<green>Welcome <name>");

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MessageCatalog catalog() {
        return new MessageCatalog(
                Map.of(
                        Locale.GERMAN, Map.of("join.welcome", "<green>Willkommen <name>"),
                        Locale.ENGLISH, Map.of("join.welcome", "<green>Welcome <name>")),
                Locale.ENGLISH);
    }

    @Test
    void rendersThePlayerLocaleTemplateWithPlaceholders() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.GERMAN);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH));

        Component rendered = messages.render(player, WELCOME, Text.placeholder("name", "Steve"));

        assertThat(Text.plain(rendered)).isEqualTo("Willkommen Steve");
    }

    @Test
    void sendDeliversChatByDefault() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.ENGLISH);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH));

        messages.send(player, WELCOME, Text.placeholder("name", "Alex"));

        assertThat(Text.plain(player.nextComponentMessage())).isEqualTo("Welcome Alex");
    }

    @Test
    void aConfiguredActionBarChannelRoutesTheSameText() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.ENGLISH);
        Map<String, Message> channels = Map.of(WELCOME.path(), new Message.ActionBar("<unused>"));
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH), channels);

        messages.send(player, WELCOME, Text.placeholder("name", "Alex"));

        assertThat(player.nextComponentMessage()).isNull();
        assertThat(Text.plain(player.nextActionBar())).isEqualTo("Welcome Alex");
    }

    @Test
    void playerLocaleSourceReadsThePlayersOwnLocale() {
        PlayerMock player = server.addPlayer();
        player.setLocale(Locale.GERMAN);
        LocaleSource source = LocaleSource.ofDefault(Locale.ENGLISH);

        assertThat(source.localeOf(player)).isEqualTo(Locale.GERMAN);
    }

    @Test
    void nonPlayerAudienceFallsBackToTheDefaultLocale() {
        LocaleSource source = LocaleSource.ofDefault(Locale.FRENCH);

        assertThat(source.localeOf(net.kyori.adventure.audience.Audience.empty()))
                .isEqualTo(Locale.FRENCH);
    }
}

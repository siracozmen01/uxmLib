package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
    void aConfiguredTitleChannelRendersBothTitleAndItsSubtitle() {
        // MockBukkit does not round-trip Adventure showTitle into a readable queue, so capture the Title at
        // the Adventure layer to assert the facade renders the channel's own subtitle template, not empty.
        CapturingTitleAudience viewer = new CapturingTitleAudience();
        Message.TitleText channel = new Message.TitleText(
                "<unused>", "<gray>welcome <name>", Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO);
        Map<String, Message> channels = Map.of(WELCOME.path(), channel);
        Messages messages = new Messages(catalog(), LocaleSource.ofDefault(Locale.ENGLISH), channels);

        messages.send(viewer, WELCOME, Text.placeholder("name", "Alex"));

        net.kyori.adventure.title.Title shown = viewer.shown();
        assertThat(Text.plain(shown.title())).isEqualTo("Welcome Alex");
        assertThat(Text.plain(shown.subtitle())).isEqualTo("welcome Alex");
    }

    /** A minimal Audience that records the {@link net.kyori.adventure.title.Title} shown to it. */
    private static final class CapturingTitleAudience implements net.kyori.adventure.audience.Audience {
        private net.kyori.adventure.title.@org.jspecify.annotations.Nullable Title shown;

        @Override
        public void showTitle(net.kyori.adventure.title.Title title) {
            this.shown = title;
        }

        net.kyori.adventure.title.Title shown() {
            return java.util.Objects.requireNonNull(shown, "no title was shown");
        }
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

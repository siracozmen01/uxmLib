package com.uxplima.uxmlib.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure tests of the WebhookMessage payload: build-time validation and JSON body shape. */
class WebhookMessageTest {

    @Test
    void contentOnlyBuildsAndStillSuppressesMentions() {
        WebhookMessage message = WebhookMessage.builder().content("hello").build();
        assertThat(message.body()).isEqualTo("{\"content\":\"hello\",\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    void carriesUsernameAvatarAndThreadNameOverrides() {
        WebhookMessage message = WebhookMessage.builder()
                .content("hi")
                .username("Reporter")
                .avatarUrl("https://cdn/a.png")
                .threadName("alerts")
                .build();
        String body = message.body();
        assertThat(body).contains("\"username\":\"Reporter\"");
        assertThat(body).contains("\"avatar_url\":\"https://cdn/a.png\"");
        assertThat(body).contains("\"thread_name\":\"alerts\"");
        assertThat(body).contains("\"allowed_mentions\":{\"parse\":[]}");
    }

    @Test
    void encodesMultipleEmbedsAsAnArray() {
        WebhookMessage message = WebhookMessage.builder()
                .embeds(List.of(DiscordEmbed.of("A", "one"), DiscordEmbed.of("B", "two")))
                .build();
        assertThat(message.body())
                .isEqualTo("{\"embeds\":[{\"title\":\"A\",\"description\":\"one\"},"
                        + "{\"title\":\"B\",\"description\":\"two\"}],\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    void rejectsAWhollyBlankMessage() {
        assertThatThrownBy(() -> WebhookMessage.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> WebhookMessage.builder().content("   ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsContentBeyondTwoThousandCharacters() {
        assertThatThrownBy(
                        () -> WebhookMessage.builder().content("x".repeat(2001)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    void rejectsMoreThanTenEmbeds() {
        WebhookMessage.Builder b = WebhookMessage.builder();
        for (int i = 0; i < 11; i++) {
            b.embed(DiscordEmbed.of("t" + i, "d"));
        }
        assertThatThrownBy(b::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeds");
    }

    @Test
    void aggregatesEmbedLimitViolationsFromBuild() {
        assertThatThrownBy(() -> WebhookMessage.builder()
                        .embed(DiscordEmbed.builder().title("x".repeat(257)).build())
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void acceptsAValidContentPlusEmbedMessage() {
        assertThatCode(() -> WebhookMessage.builder()
                        .content("see report")
                        .embed(DiscordEmbed.of("Report", "all green"))
                        .build())
                .doesNotThrowAnyException();
    }
}

package com.uxplima.uxmlib.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Pure tests of the webhook JSON encoding and URL validation — no network, no Bukkit. */
class DiscordWebhookTest {

    @Test
    void wrapsContentInAJsonObject() {
        assertThat(DiscordWebhook.contentBody("hello"))
                .isEqualTo("{\"content\":\"hello\",\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    @SuppressWarnings("StringConcatToTextBlock") // a one-line JSON assertion reads clearer than a text block
    void joinsMultipleContentLinesWithNewlines() {
        DiscordWebhook hook = new DiscordWebhook("https://discord.com/api/webhooks/1/abc");
        assertThatThrownBy(() -> hook.sendContent(new String[0])).isInstanceOf(IllegalArgumentException.class);
        // The encoding is what we assert; the send itself is covered by sendContent(String).
        assertThat(DiscordWebhook.contentBody(String.join("\n", "a", "b")))
                .isEqualTo("{\"content\":\"a\\nb\",\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    void suppressesMentionsInBothBodiesByDefault() {
        assertThat(DiscordWebhook.contentBody("hi @everyone")).contains("\"allowed_mentions\":{\"parse\":[]}");
        assertThat(DiscordWebhook.embedBody(DiscordEmbed.of("t", "d"))).contains("\"allowed_mentions\":{\"parse\":[]}");
    }

    @Test
    void acceptsAWellFormedHttpsUrl() {
        assertThatCode(() -> new DiscordWebhook("https://discord.com/api/webhooks/1/abc"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAMalformedUrlAtConstruction() {
        assertThatThrownBy(() -> new DiscordWebhook("not a url")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscordWebhook("ftp://example.com/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapesJsonSpecialCharacters() {
        assertThat(DiscordWebhook.jsonString("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
        assertThat(DiscordWebhook.jsonString("line1\nline2")).isEqualTo("\"line1\\nline2\"");
        assertThat(DiscordWebhook.jsonString("tab\tend")).isEqualTo("\"tab\\tend\"");
    }

    @Test
    void leavesPlainTextUntouched() {
        assertThat(DiscordWebhook.jsonString("plain text 123")).isEqualTo("\"plain text 123\"");
    }

    @Test
    void wrapsMultipleEmbedsInOneArray() {
        String body = DiscordWebhook.embedsBody(List.of(DiscordEmbed.of("A", "one"), DiscordEmbed.of("B", "two")));
        assertThat(body)
                .isEqualTo("{\"embeds\":[{\"title\":\"A\",\"description\":\"one\"},"
                        + "{\"title\":\"B\",\"description\":\"two\"}],\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    void rejectsMoreThanTenEmbedsAtSend() {
        DiscordWebhook hook = new DiscordWebhook("https://discord.com/api/webhooks/1/abc");
        List<DiscordEmbed> eleven = java.util.stream.IntStream.range(0, 11)
                .mapToObj(i -> DiscordEmbed.of("t" + i, "d"))
                .toList();
        assertThatThrownBy(() -> hook.sendEmbeds(eleven)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmbedsOverTheCombinedMessageBudgetAtSend() {
        // Five 2000-char embeds total 10000 chars: each is valid alone, but the message budget is 6000.
        DiscordWebhook hook = new DiscordWebhook("https://discord.com/api/webhooks/1/abc");
        List<DiscordEmbed> overBudget = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i ->
                        DiscordEmbed.builder().description("z".repeat(2000)).build())
                .toList();
        assertThatThrownBy(() -> hook.sendEmbeds(overBudget))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("combined");
    }

    @Test
    void retriesOnlyOnRateLimitWithinTheCap() {
        // 429 within the retry budget -> retry; any non-429, or a spent budget, -> no retry.
        assertThat(DiscordWebhook.shouldRetry(429, 1)).isTrue();
        assertThat(DiscordWebhook.shouldRetry(429, 0)).isFalse();
        assertThat(DiscordWebhook.shouldRetry(204, 1)).isFalse();
        assertThat(DiscordWebhook.shouldRetry(500, 1)).isFalse();
    }

    @Test
    void readsRetryAfterFromTheHeaderInSeconds() {
        assertThat(DiscordWebhook.retryDelay(Optional.of("2"), Optional.empty()))
                .isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void readsFractionalRetryAfterFromTheJsonBodyWhenHeaderIsAbsent() {
        // Discord's JSON gives retry_after in seconds as a float (e.g. 0.75s).
        assertThat(DiscordWebhook.retryDelay(Optional.empty(), Optional.of("{\"retry_after\":0.75}")))
                .isEqualTo(Duration.ofMillis(750));
    }

    @Test
    void fallsBackToAShortDelayWhenNoRetryAfterIsGiven() {
        Duration delay = DiscordWebhook.retryDelay(Optional.empty(), Optional.of("{}"));
        assertThat(delay).isGreaterThan(Duration.ZERO).isLessThanOrEqualTo(Duration.ofSeconds(5));
    }
}

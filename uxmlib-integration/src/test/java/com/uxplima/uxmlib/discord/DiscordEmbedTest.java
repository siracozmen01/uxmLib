package com.uxplima.uxmlib.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure tests of the embed JSON encoding — no network, no Bukkit. */
class DiscordEmbedTest {

    @Test
    void encodesTitleAndDescription() {
        String body = DiscordWebhook.embedBody(DiscordEmbed.of("Title", "Body"));
        assertThat(body).isEqualTo("{\"embeds\":[{\"title\":\"Title\",\"description\":\"Body\"}]}");
    }

    @Test
    void includesColourWhenSet() {
        String body = DiscordWebhook.embedBody(DiscordEmbed.colored("T", "D", 0xFF8800));
        assertThat(body).isEqualTo("{\"embeds\":[{\"title\":\"T\",\"description\":\"D\",\"color\":16745472}]}");
    }

    @Test
    void escapesSpecialCharactersInFields() {
        String body = DiscordWebhook.embedBody(DiscordEmbed.of("a\"b", "line1\nline2"));
        assertThat(body).contains("\"title\":\"a\\\"b\"").contains("\"description\":\"line1\\nline2\"");
    }

    @Test
    void colorValueReflectsPresence() {
        assertThat(DiscordEmbed.of("t", "d").colorValue()).isEmpty();
        assertThat(DiscordEmbed.colored("t", "d", 1).colorValue()).contains(1);
    }
}

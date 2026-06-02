package com.uxplima.uxmlib.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.kyori.adventure.text.format.NamedTextColor;

import org.junit.jupiter.api.Test;

/** Pure tests of the embed JSON encoding — no network, no Bukkit. */
class DiscordEmbedTest {

    @Test
    void encodesTitleAndDescription() {
        String body = DiscordWebhook.embedBody(DiscordEmbed.of("Title", "Body"));
        assertThat(body)
                .isEqualTo(
                        "{\"embeds\":[{\"title\":\"Title\",\"description\":\"Body\"}],\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    void includesColourWhenSet() {
        // 0xFF8800 == 16746496 decimal — Discord embed colours are the decimal RGB integer.
        String body = DiscordWebhook.embedBody(DiscordEmbed.colored("T", "D", 0xFF8800));
        assertThat(body)
                .isEqualTo(
                        "{\"embeds\":[{\"title\":\"T\",\"description\":\"D\",\"color\":16746496}],\"allowed_mentions\":{\"parse\":[]}}");
    }

    @Test
    void escapesSpecialCharactersInFields() {
        String body = DiscordWebhook.embedBody(DiscordEmbed.of("a\"b", "line1\nline2"));
        assertThat(body).contains("\"title\":\"a\\\"b\"").contains("\"description\":\"line1\\nline2\"");
    }

    @Test
    void escapesControlCharactersAsUnicode() {
        // A bare 0x01 is illegal unescaped in JSON and must become \\u0001; backspace (0x08) and form-feed
        // (0x0C) get their short escapes \\b and \\f. Build the control chars explicitly to be unambiguous.
        String title = "a" + (char) 0x01 + "b";
        String description = "c" + (char) 0x08 + "d" + (char) 0x0C + "e";
        String body = DiscordWebhook.embedBody(DiscordEmbed.of(title, description));
        assertThat(body).contains("\"title\":\"a\\u0001b\"").contains("\"description\":\"c\\bd\\fe\"");
    }

    @Test
    void colorValueReflectsPresence() {
        assertThat(DiscordEmbed.of("t", "d").colorValue()).isEmpty();
        assertThat(DiscordEmbed.colored("t", "d", 1).colorValue()).contains(1);
    }

    @Test
    void encodesAuthorFooterAndFields() {
        DiscordEmbed embed = DiscordEmbed.builder()
                .title("Report")
                .author("Bot", "https://x", "https://icon.png")
                .footer("v1.0", null)
                .field("Status", "OK", true)
                .field("Count", "3", true)
                .build();
        String body = DiscordWebhook.embedBody(embed);

        assertThat(body)
                .contains("\"author\":{\"name\":\"Bot\",\"url\":\"https://x\",\"icon_url\":\"https://icon.png\"}");
        assertThat(body).contains("\"footer\":{\"text\":\"v1.0\"}"); // no icon_url -> omitted
        assertThat(body).contains("\"fields\":[{\"name\":\"Status\",\"value\":\"OK\",\"inline\":true}");
    }

    @Test
    void encodesThumbnailImageAndTimestamp() {
        DiscordEmbed embed = DiscordEmbed.builder()
                .title("T")
                .thumbnail("https://t.png")
                .image("https://i.png")
                .timestamp(java.time.Instant.parse("2020-01-01T00:00:00Z"))
                .build();
        String body = DiscordWebhook.embedBody(embed);

        assertThat(body).contains("\"thumbnail\":{\"url\":\"https://t.png\"}");
        assertThat(body).contains("\"image\":{\"url\":\"https://i.png\"}");
        assertThat(body).contains("\"timestamp\":\"2020-01-01T00:00:00Z\"");
    }

    @Test
    void closureSubBuildersMatchTheFlatAuthorFooterAndField() {
        DiscordEmbed nested = DiscordEmbed.builder()
                .title("Report")
                .author(author -> author.name("Bot").url("https://x").iconUrl("https://icon.png"))
                .footer(footer -> footer.text("v1.0"))
                .field(field -> field.name("Status").value("OK").inline(true))
                .build();
        DiscordEmbed flat = DiscordEmbed.builder()
                .title("Report")
                .author("Bot", "https://x", "https://icon.png")
                .footer("v1.0", null)
                .field("Status", "OK", true)
                .build();

        assertThat(DiscordWebhook.embedBody(nested)).isEqualTo(DiscordWebhook.embedBody(flat));
    }

    @Test
    void colorFromRgbComponentsPacksToTheDecimalInteger() {
        // 0xFF8800 == 16746496 decimal — the same colour the int overload encodes.
        DiscordEmbed embed =
                DiscordEmbed.builder().title("T").color(0xFF, 0x88, 0x00).build();
        assertThat(embed.colorValue()).contains(0xFF8800);
    }

    @Test
    void colorFromRgbComponentsRejectsAnOutOfRangeChannel() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> DiscordEmbed.builder().color(256, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void colorFromAnRgbLikePacksTheSameWayAsTheComponents() {
        DiscordEmbed fromRgbLike =
                DiscordEmbed.builder().color(NamedTextColor.RED).build();
        assertThat(fromRgbLike.colorValue()).contains(NamedTextColor.RED.value());
    }

    @Test
    void multilineDescriptionFromVarargsJoinsWithNewlines() {
        DiscordEmbed embed =
                DiscordEmbed.builder().title("T").description("a", "b", "c").build();
        assertThat(embed.description()).isEqualTo("a\nb\nc");
    }

    @Test
    void multilineDescriptionFromListJoinsWithNewlines() {
        DiscordEmbed embed = DiscordEmbed.builder()
                .title("T")
                .description(List.of("one", "two"))
                .build();
        assertThat(embed.description()).isEqualTo("one\ntwo");
    }

    @Test
    void omitsUnsetFields() {
        // A title-only embed must not emit description/color/author keys.
        String body =
                DiscordWebhook.embedBody(DiscordEmbed.builder().title("Only").build());
        assertThat(body).isEqualTo("{\"embeds\":[{\"title\":\"Only\"}],\"allowed_mentions\":{\"parse\":[]}}");
    }
}

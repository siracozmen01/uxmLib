package com.uxplima.uxmlib.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure tests of the documented Discord embed-limit rules. */
class EmbedLimitsTest {

    @Test
    void aValidEmbedHasNoViolations() {
        assertThat(EmbedLimits.violations(DiscordEmbed.of("Title", "Body"))).isEmpty();
    }

    @Test
    void rejectsAnOverlongTitle() {
        DiscordEmbed embed = DiscordEmbed.builder().title("x".repeat(257)).build();
        assertThat(EmbedLimits.violations(embed)).anyMatch(v -> v.contains("title"));
    }

    @Test
    void rejectsAnOverlongDescription() {
        DiscordEmbed embed =
                DiscordEmbed.builder().description("y".repeat(4097)).build();
        assertThat(EmbedLimits.violations(embed)).anyMatch(v -> v.contains("description"));
    }

    @Test
    void rejectsMoreThanTwentyFiveFields() {
        DiscordEmbed.Builder b = DiscordEmbed.builder().title("t");
        for (int i = 0; i < 26; i++) {
            b.field("n" + i, "v", false);
        }
        assertThat(EmbedLimits.violations(b.build())).anyMatch(v -> v.contains("fields"));
    }

    @Test
    void rejectsAnOverlongFieldValue() {
        DiscordEmbed embed =
                DiscordEmbed.builder().field("name", "v".repeat(1025), false).build();
        assertThat(EmbedLimits.violations(embed)).anyMatch(v -> v.contains("field"));
    }

    @Test
    void rejectsAnEmbedExceedingTheTotalCharacterBudget() {
        // Title (256) + description (4096) + a field name+value pushing past the 6000 total.
        DiscordEmbed embed = DiscordEmbed.builder()
                .title("a".repeat(256))
                .description("b".repeat(4096))
                .field("c".repeat(256), "d".repeat(1024), false)
                .field("e".repeat(256), "f".repeat(1024), false)
                .build();
        assertThat(EmbedLimits.violations(embed)).anyMatch(v -> v.contains("total"));
    }
}

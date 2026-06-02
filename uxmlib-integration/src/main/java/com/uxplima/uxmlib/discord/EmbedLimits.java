package com.uxplima.uxmlib.discord;

import java.util.ArrayList;
import java.util.List;

/**
 * The documented Discord embed limits, and a check that returns every limit a {@link DiscordEmbed} breaks.
 * Discord rejects an over-limit or blank embed with a 400, so a caller is better served by a build-time
 * {@link IllegalArgumentException} that names the problem than by a network error after the fact. The check
 * aggregates rather than failing on the first violation so one pass surfaces them all.
 *
 * <p>Limits per the Discord API reference: title 256, description 4096, up to 25 fields (name 256 / value
 * 1024), footer text 2048, author name 256, and a combined 6000-character budget across an embed's text.
 */
final class EmbedLimits {

    static final int TITLE_MAX = 256;
    static final int DESCRIPTION_MAX = 4096;
    static final int FIELDS_MAX = 25;
    static final int FIELD_NAME_MAX = 256;
    static final int FIELD_VALUE_MAX = 1024;
    static final int FOOTER_MAX = 2048;
    static final int AUTHOR_NAME_MAX = 256;
    static final int EMBED_TOTAL_MAX = 6000;

    private EmbedLimits() {}

    /** Every limit the embed breaks, in declaration order; empty when the embed is within bounds. */
    static List<String> violations(DiscordEmbed embed) {
        List<String> out = new ArrayList<>();
        cap(out, "title", embed.title(), TITLE_MAX);
        cap(out, "description", embed.description(), DESCRIPTION_MAX);
        if (embed.footer() != null) {
            cap(out, "footer text", embed.footer().text(), FOOTER_MAX);
        }
        if (embed.author() != null) {
            cap(out, "author name", embed.author().name(), AUTHOR_NAME_MAX);
        }
        checkFields(out, embed.fields());
        if (totalLength(embed) > EMBED_TOTAL_MAX) {
            out.add("embed total characters exceed " + EMBED_TOTAL_MAX);
        }
        return out;
    }

    private static void checkFields(List<String> out, List<DiscordEmbed.Field> fields) {
        if (fields.size() > FIELDS_MAX) {
            out.add("embed has more than " + FIELDS_MAX + " fields");
        }
        for (DiscordEmbed.Field field : fields) {
            cap(out, "field name", field.name(), FIELD_NAME_MAX);
            cap(out, "field value", field.value(), FIELD_VALUE_MAX);
        }
    }

    private static void cap(List<String> out, String label, @org.jspecify.annotations.Nullable String value, int max) {
        if (value != null && value.length() > max) {
            out.add(label + " exceeds " + max + " characters");
        }
    }

    private static int totalLength(DiscordEmbed embed) {
        int total = length(embed.title()) + length(embed.description());
        if (embed.footer() != null) {
            total += embed.footer().text().length();
        }
        if (embed.author() != null) {
            total += embed.author().name().length();
        }
        for (DiscordEmbed.Field field : embed.fields()) {
            total += field.name().length() + field.value().length();
        }
        return total;
    }

    private static int length(@org.jspecify.annotations.Nullable String value) {
        return value == null ? 0 : value.length();
    }
}

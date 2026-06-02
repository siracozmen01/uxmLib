package com.uxplima.uxmlib.advancement;

import java.util.Objects;

/**
 * Builds the data-pack JSON for the synthetic advancement that drives a toast. The shape mirrors a vanilla
 * advancement: a single {@code minecraft:impossible} criterion (named {@link #CRITERION}, which by
 * definition can never trip on its own, so the advancement only ever completes when we award it by hand)
 * and a {@code display} block carrying the icon, title, description and frame. {@code show_toast} is on so
 * the client animates the toast; {@code announce_to_chat} and {@code hidden} are off so nothing is posted
 * to chat and the entry never appears in the advancement screen.
 *
 * <p>This is deliberately a pure string builder with no Bukkit or Adventure types: the caller resolves the
 * icon to its item id and serialises the title/description to component JSON, then hands those strings in.
 * That keeps the exact JSON unit-testable without standing up a server.
 */
final class ToastAdvancementJson {

    /** The single impossible criterion the toast advancement carries; awarded by hand to fire the toast. */
    static final String CRITERION = "uxmlib_toast";

    private ToastAdvancementJson() {}

    /**
     * The advancement JSON for an icon item, a title and description already serialised to component JSON,
     * and a frame. {@code iconItemId} is a namespaced item id (for example {@code minecraft:diamond});
     * {@code titleJson} and {@code descriptionJson} are component JSON values (an object such as
     * {@code {"text":"Hi"}}) and are embedded verbatim, so they must be valid JSON the caller produced
     * through a component serialiser.
     */
    static String build(String iconItemId, String titleJson, String descriptionJson, AdvancementFrame frame) {
        Objects.requireNonNull(iconItemId, "iconItemId");
        Objects.requireNonNull(titleJson, "titleJson");
        Objects.requireNonNull(descriptionJson, "descriptionJson");
        Objects.requireNonNull(frame, "frame");
        String display = "\"display\":{\"icon\":{\"id\":" + quote(iconItemId) + "},\"title\":" + titleJson
                + ",\"description\":" + descriptionJson + ",\"frame\":" + quote(frame.json())
                + ",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true}";
        String criteria = "\"criteria\":{" + quote(CRITERION) + ":{\"trigger\":\"minecraft:impossible\"}}";
        return "{" + display + "," + criteria + "}";
    }

    private static String quote(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}

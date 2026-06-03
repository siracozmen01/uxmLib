package com.uxplima.uxmlib.text;

import java.util.Map;
import java.util.Objects;

/**
 * Pure expander for the opt-in {@code prefix:<key>} marker in message templates. When a template
 * <em>starts</em> with {@code "prefix:<key>"}, the named prefix is looked up in {@code prefixes} and
 * prepended in place of the marker; the rest of the template follows.
 *
 * <p>Unknown keys strip the marker and leave the body (validation happens at config-load, so no logging
 * here). Templates without the marker — or with the marker not at the very start — pass through verbatim.
 *
 * <p>The marker is opt-in (rather than always-prepend) so most lines (chat bodies, error toasts) carry no
 * server prefix, while the operator opts the specific lines that should into it.
 */
public final class LangPrefixExpander {

    private LangPrefixExpander() {}

    private static final String MARKER = "prefix:";

    /** Expand a leading {@code prefix:<key>} marker; otherwise return {@code template} verbatim. */
    public static String expand(String template, Map<String, String> prefixes) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(prefixes, "prefixes");
        if (!template.startsWith(MARKER)) {
            return template;
        }
        int afterMarker = MARKER.length();
        int end = afterMarker;
        while (end < template.length() && isKeyChar(template.charAt(end))) {
            end++;
        }
        String key = template.substring(afterMarker, end);
        String rest = template.substring(end);
        // A leading space immediately after the key is the conventional separator; drop exactly one so
        // "prefix:server Hello" → "<prefix>Hello".
        if (rest.startsWith(" ")) {
            rest = rest.substring(1);
        }
        return prefixes.getOrDefault(key, "") + rest;
    }

    private static boolean isKeyChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }
}

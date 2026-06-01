package com.uxplima.uxmlib.text.message;

import java.util.Objects;

/**
 * A typed handle for one user-facing message: a dotted {@code path} that addresses the template in a lang
 * file, plus the {@code defaultTemplate} (a MiniMessage string) baked into the calling code. The default is
 * the lowest fallback tier and the seed an operator gets when no translation exists, so a consumer never
 * ships a message with no text.
 *
 * <p>Consumers usually declare their keys as an enum implementing this interface, which keeps every path and
 * its English default in one auditable place; {@link MessageCatalog#defaults(Iterable)} can then harvest the
 * whole set into a starter lang file.
 */
public interface MessageKey {

    /** The dotted path addressing this message in a lang file, e.g. {@code "join.welcome"}. */
    String path();

    /** The built-in MiniMessage template used when no lang file supplies one. Never {@code null} or blank. */
    String defaultTemplate();

    /** A bare key/default pair, for callers that do not declare an enum of keys. */
    static MessageKey of(String path, String defaultTemplate) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(defaultTemplate, "defaultTemplate");
        if (path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (defaultTemplate.isBlank()) {
            throw new IllegalArgumentException("defaultTemplate must not be blank");
        }
        return new SimpleMessageKey(path, defaultTemplate);
    }

    /** The minimal value carrier behind {@link #of(String, String)}. */
    record SimpleMessageKey(String path, String defaultTemplate) implements MessageKey {}
}

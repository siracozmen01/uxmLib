package com.uxplima.uxmlib.hook.placeholder;

import java.util.Objects;

import com.uxplima.uxmlib.hook.PlaceholderApi;

/**
 * Registers the shared {@link UxmPlaceholderExpansion} with PlaceholderAPI, exposing a consumer's
 * {@link PlaceholderRegistry} as {@code %uxm_<prefix>_<params>%} placeholders. This is the write side of the
 * PAPI integration; {@link PlaceholderApi} is the read side.
 *
 * <p>The expansion is created and registered only past {@link Placeholders#isAvailable()}, so the
 * {@code me.clip} classes are touched solely when PlaceholderAPI is present: a server without it still
 * loads. {@link #register} is a no-op returning {@code false} when PlaceholderAPI is absent, so callers can
 * invoke it unconditionally at startup.
 */
public final class PlaceholderExpansions {

    /** The default PlaceholderAPI identifier, yielding {@code %uxm_...%} placeholders. */
    public static final String DEFAULT_IDENTIFIER = "uxm";

    private PlaceholderExpansions() {}

    /**
     * Register {@code registry}'s providers under the default {@code uxm} identifier. Returns whether the
     * expansion registered (false when PlaceholderAPI is absent or PlaceholderAPI rejected it).
     *
     * <p><strong>Use this only if yours is the one plugin on the server registering it.</strong>
     * PlaceholderAPI keeps a single expansion per identifier, so on a server running two plugins that both
     * take the default, the first one wins and the second's placeholders quietly resolve to nothing. A
     * plugin that ships to servers it does not control should name its own identifier through
     * {@link #register(String, PlaceholderRegistry, String, String)}: one per plugin, not one per library.
     */
    public static boolean register(PlaceholderRegistry registry, String author, String version) {
        return register(DEFAULT_IDENTIFIER, registry, author, version);
    }

    /**
     * Register {@code registry}'s providers under {@code identifier}, yielding {@code %<identifier>_...%}
     * placeholders. Returns whether the expansion registered; a no-op returning {@code false} when
     * PlaceholderAPI is not installed.
     */
    public static boolean register(String identifier, PlaceholderRegistry registry, String author, String version) {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(version, "version");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        if (!PlaceholderApi.isAvailable()) {
            return false;
        }
        return new UxmPlaceholderExpansion(identifier, author, version, registry).register();
    }
}

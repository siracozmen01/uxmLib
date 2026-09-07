package com.uxplima.uxmlib.claim;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * The narrow read seam {@link ClaimProvidersConfig} takes its operator choices through. It is three methods
 * wide on purpose: the library must not decide how a plugin stores its configuration, and a plugin that
 * already holds a HOCON tree, a YAML tree or a map adapts to this in a handful of lines.
 *
 * <p>Paths are dotted, the shape every configuration reader in this family already speaks
 * ({@code claims.providers.lands}). Each getter takes the value to return when the path is absent, so an
 * operator who wrote nothing gets the documented default rather than an exception.
 */
@NullMarked
public interface ClaimSettings {

    /** The child keys of the map at {@code path}, or an empty list when the path is absent or not a map. */
    List<String> keys(String path);

    /** The boolean at {@code path}, or {@code fallback} when it is absent. */
    boolean getBoolean(String path, boolean fallback);

    /** The string at {@code path}, or {@code fallback} when it is absent. */
    String getString(String path, String fallback);

    /** An empty source: every path is absent, so every reader sees its own default. */
    static ClaimSettings empty() {
        return new ClaimSettings() {

            @Override
            public List<String> keys(String path) {
                return List.of();
            }

            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }
        };
    }
}

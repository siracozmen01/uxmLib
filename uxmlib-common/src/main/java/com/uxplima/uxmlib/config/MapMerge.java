package com.uxplima.uxmlib.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Merges a managed default map with a user-overridden one. The managed map carries the keys the library
 * ships and guarantees exist; the user map carries operator edits. For any key present in both, the user's
 * value wins; managed-only keys fill the gaps so a new default added in an upgrade is never lost.
 *
 * <p>Iteration order is deterministic: managed keys first (in their order), then any extra user keys the
 * managed map does not know about. The result is a fresh map; neither input is mutated.
 *
 * <p>Package private. It was published, and nothing in this library or its README ever named it, so it was
 * surface we owed a consumer without ever offering it to one. Out of the published API it can change, or
 * find the caller it was written for, without either being a break.
 */
final class MapMerge {

    private MapMerge() {}

    /**
     * The per-key merge of {@code managed} (defaults) and {@code user} (overrides). User values take
     * precedence for shared keys; managed-only keys are kept; user-only keys are appended.
     */
    public static <K, V> Map<K, V> userWins(Map<K, V> managed, Map<K, V> user) {
        Objects.requireNonNull(managed, "managed");
        Objects.requireNonNull(user, "user");
        Map<K, V> merged = new LinkedHashMap<>(managed);
        merged.putAll(user);
        return merged;
    }
}

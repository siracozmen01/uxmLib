package com.uxplima.uxmlib.text.message;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * Builds a {@link MessageCatalog} from one Configurate tree per locale, flattening each tree into the
 * {@code path -> template} map the catalog resolves against. A tree is walked depth-first; every leaf node
 * with a string value becomes one entry whose path is its dotted key chain (so {@code join { welcome = "..." }}
 * yields the path {@code join.welcome}). Non-string leaves are skipped, which lets a lang file carry the
 * richer {@code {type, text}} channel section without breaking the flat lookup.
 */
public final class MessageCatalogLoader {

    private MessageCatalogLoader() {}

    /**
     * Build a catalog from {@code trees}, one per locale.
     *
     * @param trees a locale mapped to that locale's loaded HOCON root node
     * @param defaultLocale the fallback locale (need not be present in {@code trees})
     */
    public static MessageCatalog fromNodes(Map<Locale, ConfigurationNode> trees, Locale defaultLocale) {
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Map<Locale, Map<String, String>> templates = new HashMap<>();
        for (var entry : trees.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "locale");
            Map<String, String> flat = new LinkedHashMap<>();
            flatten(entry.getValue(), "", flat);
            templates.put(entry.getKey(), flat);
        }
        return new MessageCatalog(templates, defaultLocale);
    }

    private static void flatten(ConfigurationNode node, String prefix, Map<String, String> out) {
        if (node.isMap()) {
            for (var child : node.childrenMap().entrySet()) {
                flatten(child.getValue(), join(prefix, String.valueOf(child.getKey())), out);
            }
            return;
        }
        String value = node.getString();
        if (value != null && !prefix.isEmpty()) {
            out.put(prefix, value);
        }
    }

    private static String join(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }
}

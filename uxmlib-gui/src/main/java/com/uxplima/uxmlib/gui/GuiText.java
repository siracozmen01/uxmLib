package com.uxplima.uxmlib.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Where a menu gets its words. The consumer answers; this library never does.
 *
 * <p>Two questions live here because a menu asks two different ones, and merging them would push raw text
 * through a catalog it was never in. {@link #text} looks a key up, which is what a screen does when a file
 * names {@code @menu.category.title} instead of holding the words. {@link #render} takes text an operator
 * already wrote in a {@code .conf} and turns it into a component, which is what a screen does for every name
 * and every lore line of every item. Different input, different frequency, and only the first has a key.
 *
 * <p><strong>No implementation ships with this library, and that is deliberate.</strong> A default that
 * resolved a key would decide what a key means; one that read {@code viewer.locale()} would decide what
 * language a player reads in, and would outrank whatever answer the consumer's own language command had
 * already given. The {@code viewer} passed here carries identity and never language: what to do with it is
 * the consumer's, including which locale it implies.
 *
 * <p>A {@code Player} rather than a {@code UUID} because every caller already holds one, and because an
 * implementation usually wants the name as well as the id and should not have to look a player up to get
 * back something it was handed.
 *
 * <p>Implementations are called on the viewer's entity thread, once per item per render, so they should read
 * from memory and not from disk or a database.
 */
public interface GuiText {

    /**
     * The words {@code key} stands for, drawn for {@code viewer}, with {@code placeholders} substituted into
     * them. Never null: a key an implementation does not know still has to render as something, because the
     * alternative is a hole in a menu a player is looking at.
     */
    Component text(Player viewer, String key, Map<String, String> placeholders);

    /** Text an operator wrote, turned into a component. No key, no catalog, no lookup. */
    Component render(String raw);

    /** The same as {@link #text}, for the many keys that carry no placeholders. */
    default Component text(Player viewer, String key) {
        return text(viewer, key, Map.of());
    }

    /**
     * {@link #text} flattened to a plain string, for the places a component cannot go: a Bedrock form label
     * is a flat string, and so is an inventory title on some paths. Formatting is dropped, not stripped from
     * the source, so the same key reads the same in both places.
     */
    default String plain(Player viewer, String key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return PlainTextComponentSerializer.plainText().serialize(text(viewer, key, placeholders));
    }
}

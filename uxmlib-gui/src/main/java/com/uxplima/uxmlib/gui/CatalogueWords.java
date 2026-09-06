package com.uxplima.uxmlib.gui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.gui.style.MenuTiles;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;

/**
 * What a menu file writes, turned into what a player reads.
 *
 * <p>A menu file names no colour and holds no language. A line that starts with {@code @} is a key of the
 * message catalogue, so the words are translated with the rest and painted from {@code theme.conf}. A line
 * that does not is written as it stands, with the roles of the theme applied to it, so an operator who wants
 * one word of their own still gets the colours of the server.
 *
 * <p>A line that starts with {@code tile:} is a whole tile rather than one line, and {@link MenuTiles} draws
 * it in the six blocks the canon fixes. Everything else is one line of words.
 *
 * <p>This is a {@link GuiText}, which is the seam the menu engine asks its words through. The engine hands a
 * key to {@link #text} once it has taken the {@code @} off, and a written line to {@link #renderFor} with the
 * viewer who is looking at it, which is what a tile needs and a viewer-less render cannot give.
 *
 * <p>A value of a row goes in as a placeholder and never as text. A player who named their item
 * {@code <red>} sees those characters on the tile: they do not repaint it.
 */
public final class CatalogueWords implements GuiText {

    /**
     * What a window is about, for the viewer who opened it.
     *
     * <p>A tile of a window that was opened on one thing, a listing or an arena, is about that thing, and no
     * row of a list carries it. A plugin that has such a window gives one of these; a plugin whose windows
     * are about nothing but their rows does not.
     */
    @FunctionalInterface
    public interface Window {

        /** The values of the window this player has open, which is empty when they opened a plain one. */
        Map<String, String> valuesOf(UUID viewer);

        /** A window about nothing but its own rows. */
        static Window none() {
            return viewer -> Map.of();
        }
    }

    private final Messages messages;
    private final Styler styler;
    private final Window window;
    private final MenuTiles tiles;

    /** Words for a plugin whose windows are about nothing but their rows. */
    public CatalogueWords(Messages messages, Styler styler) {
        this(messages, styler, Window.none());
    }

    public CatalogueWords(Messages messages, Styler styler, Window window) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
        this.window = Objects.requireNonNull(window, "window");
        this.tiles = new MenuTiles(messages, styler);
    }

    /**
     * The words a key stands for, in the language of the viewer. The engine has already taken the {@code @}
     * off, so what arrives is the path.
     *
     * <p>The path is its own last answer: a key an operator invented and never translated shows the key on the
     * tile rather than an empty line, which is the cue that it needs a line.
     */
    @Override
    public Component text(Player viewer, String key, Map<String, String> values) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        return messages.render(viewer, MessageKey.of(key, key), resolvers(all(viewer, values)));
    }

    /**
     * A written line with no viewer to write it for. A tile cannot be drawn here, because which language it
     * reads in is the viewer's answer, so this is the plain reading: the roles of the theme over the words as
     * the operator wrote them, in the catalogue's own language.
     */
    @Override
    public Component render(String raw) {
        Objects.requireNonNull(raw, "raw");
        return Text.mini(styler.apply(raw, defaultLocale()));
    }

    /** A written line for the viewer who is looking at it, which is the only place a tile can be drawn. */
    @Override
    public Component renderFor(Player viewer, String raw, Map<String, String> values) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(raw, "raw");
        TagResolver[] resolvers = resolvers(all(viewer, values));
        if (MenuTiles.marks(raw)) {
            return tiles.lore(viewer, raw, resolvers);
        }
        return Text.mini(styler.apply(raw, messages.localeOf(viewer)), resolvers);
    }

    private Locale defaultLocale() {
        return messages.catalog().defaultLocale();
    }

    /**
     * The values of the window and the values of the row together.
     *
     * <p>A row of a computed list wins where the two name the same thing: a tile of a list is about that row.
     */
    private Map<String, String> all(Player viewer, Map<String, String> values) {
        Map<String, String> opened = window.valuesOf(viewer.getUniqueId());
        if (opened.isEmpty()) {
            return values;
        }
        Map<String, String> merged = new LinkedHashMap<>(opened);
        merged.putAll(values);
        return merged;
    }

    private static TagResolver[] resolvers(Map<String, String> values) {
        TagResolver[] resolvers = new TagResolver[values.size()];
        int at = 0;
        for (Map.Entry<String, String> value : values.entrySet()) {
            resolvers[at++] = Text.placeholder(value.getKey(), value.getValue());
        }
        return resolvers;
    }
}

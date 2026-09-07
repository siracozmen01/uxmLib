package com.uxplima.uxmlib.gui;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.gui.style.MenuTiles;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import org.jspecify.annotations.Nullable;

/**
 * What a menu file writes, turned into what a player reads.
 *
 * <p>A menu file names no colour and holds no language. A line that starts with {@code @} is a key of the
 * message catalogue, so the words are translated with the rest and painted from {@code theme.conf}. A line
 * that does not is written as it stands, with the roles of the theme applied to it, so an operator who wants
 * one word of their own still gets the colours of the server.
 *
 * <p>A line that starts with {@code tile:} is a whole tile rather than one line, and {@link MenuTiles} draws
 * it in the six blocks the canon fixes. Everything else is one line of words. Every way in reads the mark: the
 * written line, the key a file spelled {@code @tile:...} while the written line could not reach a viewer, and the
 * viewer-less reading. No caller of this class can hand a tile in and get the characters back.
 *
 * <p>This is a {@link GuiText}, which is the seam the menu engine asks its words through. The engine hands a
 * key to {@link #text} once it has taken the {@code @} off, and a written line to {@link #renderFor} with the
 * viewer who is looking at it, which is what a tile wants: the viewer is the language the tile reads in, and
 * {@link #render} has only the catalogue's own to fall back on.
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
     *
     * <p>A key that carries the tile mark is a tile. A menu file may write one as {@code @tile:<colour> @<key>},
     * and files in the estate do, so the mark is read here as well as on the written line and both spellings draw
     * the same tooltip. A file written from now on wants the bare {@code tile:} form, which is the one the canon
     * describes and the one a line of a lore list is read as without a catalogue lookup in front of it.
     */
    @Override
    public Component text(Player viewer, String key, Map<String, String> values) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        TagResolver named = named(viewer, values);
        if (MenuTiles.marks(key)) {
            return tiles.lore(viewer, key, named);
        }
        return messages.render(viewer, MessageKey.of(key, key), named);
    }

    /**
     * A written line with no viewer to write it for: the roles of the theme over the words as the operator wrote
     * them, in the catalogue's own language.
     *
     * <p>A tile is drawn here too, in that same language, and it carries no values because there is no line
     * spelling any and no viewer to ask about. That is a poorer answer than {@link #renderFor} gives and it is
     * still an answer: returning the line as it stands would put {@code tile:5 @menu.hub.section} on an item for
     * a player to read, which is the one outcome no path of this class may have.
     */
    @Override
    public Component render(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (MenuTiles.marks(raw)) {
            return tiles.lore(Audience.empty(), raw);
        }
        return Text.mini(styler.apply(raw, defaultLocale()));
    }

    /** A written line for the viewer who is looking at it, which is where a tile reads the language it is in. */
    @Override
    public Component renderFor(Player viewer, String raw, Map<String, String> values) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(raw, "raw");
        TagResolver named = named(viewer, values);
        if (MenuTiles.marks(raw)) {
            return tiles.lore(viewer, raw, named);
        }
        return Text.mini(styler.apply(raw, messages.localeOf(viewer)), named);
    }

    private Locale defaultLocale() {
        return messages.catalog().defaultLocale();
    }

    /**
     * The values of the row and the values of the window, as one resolver that answers a name when the catalogue
     * asks for it.
     *
     * <p>Asking rather than telling is the whole of it. The map the engine hands over holds the {@code %token%}s
     * the written line spells, and walking it is how this used to build its resolvers. A tile spells none: it
     * names a key and its facts, and the values belong to the catalogue lines behind that key. So a walk found
     * nothing to offer, {@code menu.hub.section.title = "<name>"} met no resolver of that name, and the
     * player read the tag. The engine's map answers {@code get} for any id it can reach, one handler at a time
     * and at most once each, which is what this now uses.
     *
     * <p>A row of a computed list wins where the two name the same thing: a tile of a list is about that row.
     *
     * <p>A value goes in as text and never as markup, so a player who named their item {@code <red>} sees
     * those characters. The cost of answering by name is that an id shadows the MiniMessage tag it is spelled
     * like: a plugin that registers a placeholder called {@code red} takes {@code <red>} away from every
     * line of its own catalogue. Name a placeholder after the thing it holds and this never arises.
     */
    private TagResolver named(Player viewer, Map<String, String> values) {
        return new Values(values, window.valuesOf(viewer.getUniqueId()));
    }

    /** The two maps behind one name, the row's first and the window's second. */
    private record Values(Map<String, String> row, Map<String, String> opened) implements TagResolver {

        @Override
        public @Nullable Tag resolve(String name, ArgumentQueue arguments, Context context) {
            String value = valueOf(name);
            return value == null ? null : Tag.inserting(Component.text(value));
        }

        @Override
        public boolean has(String name) {
            return valueOf(name) != null;
        }

        private @Nullable String valueOf(String name) {
            String value = row.get(name);
            return value != null ? value : opened.get(name);
        }
    }
}

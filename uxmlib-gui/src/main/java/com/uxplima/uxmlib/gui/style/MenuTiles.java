package com.uxplima.uxmlib.gui.style;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;

/**
 * A menu tile, in the six blocks a window draws: the title, the category under it, the description, the
 * facts, and the line that says what a click does.
 *
 * <p>A menu file draws a tile by writing one lore line of the shape {@code tile:<colour> @<key> [fact ...]}.
 * The colour is a gradient of {@code theme.conf}, a role, or a place on the wheel written as a number, and it
 * is written in the file rather than in the catalogue because which tiles on a screen should look alike is a
 * question about the screen and not about the words. The key names a block of the catalogue, and the facts
 * name the rows of that block, in the order the file writes them: an operator who wants one fact fewer takes
 * a word off the line, and a translator never sees the layout at all.
 *
 * <p>A word written with a minus in front of it leaves a block out: {@code -action} draws a tile that answers
 * no click without a second block of words for it. That is what a tab of a window needs, which says the same
 * thing about itself whether or not you can click it.
 *
 * <p>The shape is the library's and the look is not. {@link Lore} holds the glyphs, the columns and the air
 * between the blocks, and reads all three from the theme, so a server that renames a glyph or a colour
 * changes every tile of every window at once.
 */
public final class MenuTiles {

    /** What a lore line starts with to draw a tile rather than a line. */
    public static final String MARK = "tile:";

    /** The word over the description block, and the word over the facts. Both are the same in every window. */
    private static final String DESCRIPTION = "menu.lore.description";

    private static final String DETAILS = "menu.lore.details";

    private static final String TITLE = ".title";

    private static final String CRUMB = ".crumb";

    private static final String TEXT = ".description";

    private static final String ACTION = ".action";

    private static final String LABEL = ".label";

    private static final String VALUE = ".value";

    private final Messages messages;
    private final Styler styler;

    public MenuTiles(Messages messages, Styler styler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.styler = Objects.requireNonNull(styler, "styler");
    }

    /** Whether {@code written} is a tile rather than one line of words. */
    public static boolean marks(String written) {
        Objects.requireNonNull(written, "written");
        return written.startsWith(MARK);
    }

    /**
     * The whole tooltip of one tile, as a single component with the line breaks in it. The item builder
     * splits them, so a tile is one entry of the {@code lore} list of the menu file and not six.
     *
     * <p>The viewer is an {@link Audience} rather than a player because what a tile takes from it is a language and
     * nothing else. A player brings their own, and every other audience brings the catalogue's default, which is the
     * honest answer for a draw that has nobody in front of it: a tile asked for with no viewer is still drawn as a
     * tile, in the language the catalogue is written in, rather than handed back as the characters an operator typed.
     */
    public Component lore(Audience viewer, String written, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(written, "written");
        Spec spec = Spec.read(written);
        Lore lore = Lore.of(styler.theme());
        if (spec.draws(CRUMB) && has(viewer, spec.key + CRUMB)) {
            lore.crumb(words(viewer, spec.key + CRUMB, resolvers));
        }
        if (spec.draws(TEXT) && has(viewer, spec.key + TEXT)) {
            lore.description(words(viewer, DESCRIPTION, resolvers), words(viewer, spec.key + TEXT, resolvers));
        }
        if (!spec.facts.isEmpty()) {
            lore.details(words(viewer, DETAILS, resolvers));
            for (String fact : spec.facts) {
                lore.row(
                        words(viewer, spec.key + "." + fact + LABEL, resolvers),
                        words(viewer, spec.key + "." + fact + VALUE, resolvers));
            }
        }
        if (spec.draws(ACTION) && has(viewer, spec.key + ACTION)) {
            lore.action(words(viewer, spec.key + ACTION, resolvers));
        }
        return Tiles.titled(styler.theme(), words(viewer, spec.key + TITLE, resolvers), lore.build(), spec.colour);
    }

    /** The line of the catalogue at {@code path}, in the language of the viewer, with the values written in. */
    private Component words(Audience viewer, String path, TagResolver... resolvers) {
        return messages.render(viewer, MessageKey.of(path, path), resolvers);
    }

    /**
     * Whether the catalogue holds a line at {@code path}.
     *
     * <p>A block that leaves one out draws a tile without it rather than a tile with the key printed on it.
     * That is what lets one shape serve a tile that answers no click and a tile that answers two.
     */
    private boolean has(Audience viewer, String path) {
        return messages.catalog()
                .find(MessageKey.of(path, path), messages.localeOf(viewer))
                .isPresent();
    }

    /**
     * What one {@code tile:} line names: the colour of the title, the block of words, the facts, and what it
     * leaves out.
     */
    private record Spec(String colour, String key, List<String> facts, Set<String> without) {

        private static final Pattern WORDS = Pattern.compile("\\s+");

        static Spec read(String written) {
            String[] words = WORDS.split(written.trim(), -1);
            String colour = words[0].substring(MARK.length());
            String key = words.length > 1 ? name(words[1]) : "";
            List<String> facts = new ArrayList<>();
            Set<String> without = new LinkedHashSet<>();
            for (int at = 2; at < words.length; at++) {
                if (words[at].startsWith("-")) {
                    without.add("." + words[at].substring(1));
                } else {
                    facts.add(words[at]);
                }
            }
            return new Spec(colour, key, List.copyOf(facts), Set.copyOf(without));
        }

        /** Whether the tile draws the block this line does not ask to leave out. */
        boolean draws(String part) {
            return !without.contains(part);
        }

        /** The key as the catalogue spells it, with the {@code @} a menu file marks a key with taken off. */
        private static String name(String written) {
            return written.startsWith("@") ? written.substring(1) : written;
        }
    }
}

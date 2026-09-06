package com.uxplima.uxmlib.text.style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.spongepowered.configurate.ConfigurationNode;

/**
 * The look of a server, read from a config file, in three layers.
 *
 * <p><b>The palette</b> is the server's own colours, under whatever names the server likes: {@code sky},
 * {@code kirmizi}, {@code brand-2}. Nothing this library ships refers to a palette name, so any of them may be
 * renamed, removed or added to without breaking a message anybody wrote.
 *
 * <p><b>The roles</b> are the jobs a colour does: {@code body}, {@code value}, {@code good}. A message file
 * names a role and never a colour, so a server that wants a red interface edits one file and every message,
 * menu and item follows. A role's value is a palette name or a hex code. The map is open: a key written in the
 * file becomes a token, so a server may name a job this library never heard of and use it in its own files.
 *
 * <p><b>The wheel</b> is an ordered list of colours that decoration is taken from. A menu of twelve tiles asks
 * for twelve arcs and gets twelve pairs of neighbours, so nothing has to be named for a screen to read as
 * twelve headings rather than one heading twelve times.
 *
 * <p>The same file holds the glyphs the structure is drawn with and which languages are written in small
 * capitals. Both are values rather than mechanism, which is why they are read from a file instead of compiled
 * in.
 *
 * <p>A key the file leaves out keeps the shipped default, so an operator may write three lines instead of
 * forty, a language nobody has answered for keeps its own letters, and a role added in a later version cannot
 * break a file written against an earlier one.
 *
 * <p>What ships is the mechanism and not a look. Every role answers, in one of the sixteen colours Minecraft
 * has always had, so a plugin that wires nothing is readable; no glyph, no category colour and no gradient is
 * decided for anybody. The file at {@code uxmlib/theme.conf} on the classpath is a starting point a consumer
 * may copy and then own.
 */
public final class Theme {

    /**
     * The roles, and the colour each one takes until a file says otherwise.
     *
     * <p>The list of names is mechanism, not taste. {@link #hasColour(String)} is what makes {@code <value>}
     * a token instead of four letters and two brackets a player reads, so a role this map forgets stops
     * being a token in every message at once. The colour behind each name is taste, and a library has none:
     * each falls back to one of the sixteen colours Minecraft has always had, which is what a server already
     * sees when nothing has painted anything. A file replaces any of them, and the file this library ships
     * shows how.
     */
    private static final Map<String, TextColor> DEFAULT_ROLES = defaultRoles();

    /**
     * The glyphs the structure of a message or a tile is drawn with, unless the file names them: none.
     *
     * <p>A glyph is furniture, and furniture is taste. A library that drew an arrow between a category and
     * a sentence would be decorating the messages of a plugin that only asked for the colours, and nothing
     * in that plugin would say where the character came from. So the mechanism ships on and the look ships
     * off: name a glyph in {@code glyphs} and everything drawn with it takes it, and the file this library
     * ships shows the set our own interfaces use.
     */
    private static final Map<String, String> DEFAULT_GLYPHS = Map.of();

    /**
     * The categories whose prefix is not the accent colour: none, until the file names them.
     *
     * <p>Which word means trouble and which word means money is a decision about one product's vocabulary,
     * so the file that holds the words holds the mapping too. A category the file does not name reads in
     * the accent colour, which is a prefix that looks deliberate rather than one that looks broken.
     */
    private static final Map<String, String> DEFAULT_CATEGORIES = Map.of();

    /**
     * The languages written in small capitals unless the file says otherwise: none of them.
     *
     * <p>Small capitals are a typeface, and a typeface is taste. A library that turned them on for English by
     * itself would repaint every message of a plugin that only wanted the colours, and the plugin's author
     * would have no line anywhere saying why. So the mechanism ships on and the look ships off: name a
     * language in {@code small-caps} and it is converted, and the file this library ships shows how.
     *
     * <p>Small capitals exist for the Latin alphabet only, so a language whose letters have no small-capital
     * form must not be named.
     */
    private static final Set<String> DEFAULT_SMALL_CAPS = Set.of();

    /** The role a colour lookup falls back to, and the colour an unlisted category prefix reads in. */
    private static final String BODY = "body";

    private static final String ACCENT = "accent";

    private static final String SEPARATOR = "separator";

    /** The block that held the roles before the palette existed. A file that still uses it keeps working. */
    private static final String LEGACY_ROLES = "colours";

    private final Map<String, TextColor> roles;
    private final List<TextColor> wheel;
    private final Map<String, List<TextColor>> gradients;
    private final Map<String, String> glyphs;
    private final Map<String, String> categories;
    private final Set<String> smallCapsLanguages;

    private Theme(
            Map<String, TextColor> roles,
            List<TextColor> wheel,
            Map<String, List<TextColor>> gradients,
            Map<String, String> glyphs,
            Map<String, String> categories,
            Set<String> smallCapsLanguages) {
        this.roles = Map.copyOf(roles);
        this.wheel = List.copyOf(wheel);
        this.gradients = Map.copyOf(gradients);
        this.glyphs = Map.copyOf(glyphs);
        this.categories = Map.copyOf(categories);
        this.smallCapsLanguages = Set.copyOf(smallCapsLanguages);
    }

    /**
     * The shipped look, used when there is no file yet or when it is empty: every role answers, in a vanilla
     * colour, and nothing else is decided. It is a theme a plugin can ship with and read, not a look.
     */
    public static Theme defaults() {
        return new Theme(DEFAULT_ROLES, List.of(), Map.of(), DEFAULT_GLYPHS, DEFAULT_CATEGORIES, DEFAULT_SMALL_CAPS);
    }

    /**
     * The look in {@code node}, with the shipped default behind every value the file leaves out.
     *
     * @throws IllegalArgumentException when the file holds something that is neither a colour nor a palette
     *     name, which is a defect an operator has to see at load rather than as a black message in the game
     */
    public static Theme from(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        Map<String, TextColor> palette = palette(node);
        return new Theme(
                roles(node, palette),
                wheel(node, palette),
                gradients(node, palette),
                glyphs(node),
                categories(node),
                smallCaps(node.node("small-caps").childrenMap()));
    }

    /** The colour of {@code role}, or the body colour when this theme does not know the role. */
    public TextColor colour(String role) {
        Objects.requireNonNull(role, "role");
        TextColor found = roles.get(role);
        return found != null ? found : Objects.requireNonNull(roles.get(BODY), BODY);
    }

    /** Whether {@code role} is a role of this theme, which is what makes a token a token. */
    public boolean hasColour(String role) {
        Objects.requireNonNull(role, "role");
        return roles.containsKey(role);
    }

    /** The hex of {@code role} as MiniMessage writes it, for example {@code #38b6ff}. */
    public String hex(String role) {
        return colour(role).asHexString().toLowerCase(Locale.ROOT);
    }

    /**
     * The colours decoration is taken from, in the order the file writes them.
     *
     * <p>Empty unless the file names them. A library that shipped a wheel of its own would be choosing a look
     * for every server that never asked for one.
     */
    public List<TextColor> wheel() {
        return wheel;
    }

    /**
     * Two neighbouring colours of the wheel, or an empty list when the file names fewer than two.
     *
     * <p>This is what a screen full of tiles paints with. The caller passes the position of the tile, so tile
     * one and tile two differ without either of them naming a colour, and the wheel wraps, so a menu longer
     * than the wheel keeps working rather than running out.
     */
    public List<TextColor> arc(int index) {
        if (wheel.size() < 2) {
            return List.of();
        }
        int start = Math.floorMod(index, wheel.size());
        return List.of(wheel.get(start), wheel.get((start + 1) % wheel.size()));
    }

    /**
     * The stops of the gradient named {@code name}, in order, or an empty list when the file names none.
     *
     * <p>A gradient is how a heading reads as finished rather than merely coloured, but it is also the first
     * thing a server wants to turn off, so nothing here is a gradient unless the file says so. One stop means
     * a flat colour, which is how an operator switches one off without deleting the key.
     *
     * <p>{@code header} is the one name this library asks for. Every other name is one a file asked to be
     * painted with, and a server writes as many as its interface has moods.
     */
    public List<TextColor> gradient(String name) {
        Objects.requireNonNull(name, "name");
        return gradients.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    /**
     * The glyph named {@code name}, or an empty string when this theme does not know it. Empty rather than a
     * stand-in character: a glyph nobody configured should leave a gap, not print a question mark at a player.
     */
    public String glyph(String name) {
        Objects.requireNonNull(name, "name");
        return glyphs.getOrDefault(name, "");
    }

    /** The glyph between a message's category word and the sentence, short for {@code glyph("separator")}. */
    public String separator() {
        return glyph(SEPARATOR);
    }

    /** The colour role of the category prefix {@code label} carries; anything unlisted reads in the accent. */
    public String categoryRole(String label) {
        Objects.requireNonNull(label, "label");
        return categories.getOrDefault(label.toLowerCase(Locale.ROOT), ACCENT);
    }

    /** Whether {@code locale} is written in small capitals. */
    public boolean smallCaps(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return smallCapsLanguages.contains(locale.getLanguage().toLowerCase(Locale.ROOT));
    }

    /**
     * Every colour the file names, under the file's own names. A palette entry is always a hex code.
     *
     * <p>The map is used while the file is read and then dropped. Nothing outside the file speaks these
     * names, so keeping them would only invite a call site to depend on one.
     */
    private static Map<String, TextColor> palette(ConfigurationNode node) {
        Map<String, TextColor> palette = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.node("palette").childrenMap().entrySet()) {
            String hex = child.getValue().getString();
            if (hex != null) {
                palette.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), parse(hex));
            }
        }
        return palette;
    }

    /**
     * The roles: the shipped ones, then every role the file writes applied on top.
     *
     * <p>The map is open on purpose. A closed list would drop a role a server invented, silently, which is
     * how the file stopped being the server's and became ours. The two blocks are read in order, so a file
     * that still writes the old {@code colours} block keeps working and a {@code roles} entry wins.
     */
    private static Map<String, TextColor> roles(ConfigurationNode node, Map<String, TextColor> palette) {
        Map<String, TextColor> roles = new LinkedHashMap<>(DEFAULT_ROLES);
        readColours(node.node(LEGACY_ROLES), palette, roles);
        readColours(node.node("roles"), palette, roles);
        return roles;
    }

    private static void readColours(
            ConfigurationNode node, Map<String, TextColor> palette, Map<String, TextColor> into) {
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.childrenMap().entrySet()) {
            String value = child.getValue().getString();
            if (value != null) {
                into.put(String.valueOf(child.getKey()), resolve(value, palette));
            }
        }
    }

    /** The wheel, in the order the file writes it. Each entry is a palette name or a hex code. */
    private static List<TextColor> wheel(ConfigurationNode node, Map<String, TextColor> palette) {
        List<TextColor> wheel = new ArrayList<>();
        for (ConfigurationNode child : node.node("wheel").childrenList()) {
            String value = child.getString();
            if (value != null) {
                wheel.add(resolve(value, palette));
            }
        }
        return wheel;
    }

    /**
     * Every glyph the file names, reading {@code prefix.separator} as well as {@code glyphs.separator}. The
     * separator lived under the prefix block before the rest of the glyphs were configurable, so a file that
     * still keeps it there is still read; a {@code glyphs.separator} wins over it.
     *
     * <p>The map is open, like the roles. A name this library never heard of is still a glyph, so a file may
     * draw a part of an interface this library does not know about.
     */
    private static Map<String, String> glyphs(ConfigurationNode node) {
        Map<String, String> glyphs = new LinkedHashMap<>(DEFAULT_GLYPHS);
        String legacySeparator = node.node("prefix", SEPARATOR).getString();
        if (legacySeparator != null) {
            glyphs.put(SEPARATOR, legacySeparator);
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.node("glyphs").childrenMap().entrySet()) {
            String value = child.getValue().getString();
            if (value != null) {
                glyphs.put(String.valueOf(child.getKey()), value);
            }
        }
        return glyphs;
    }

    /** Every named list of stops the file writes, each kept in the order it writes them in. */
    private static Map<String, List<TextColor>> gradients(ConfigurationNode node, Map<String, TextColor> palette) {
        Map<String, List<TextColor>> gradients = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.node("gradients").childrenMap().entrySet()) {
            List<TextColor> stops = new ArrayList<>();
            for (ConfigurationNode stop : child.getValue().childrenList()) {
                String value = stop.getString();
                if (value != null) {
                    stops.add(resolve(value, palette));
                }
            }
            if (!stops.isEmpty()) {
                gradients.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), List.copyOf(stops));
            }
        }
        return gradients;
    }

    private static Map<String, String> categories(ConfigurationNode node) {
        Map<String, String> categories = new HashMap<>(DEFAULT_CATEGORIES);
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                node.node("prefix", "categories").childrenMap().entrySet()) {
            String role = child.getValue().getString();
            if (role != null) {
                categories.put(String.valueOf(child.getKey()).toLowerCase(Locale.ROOT), role);
            }
        }
        return categories;
    }

    /**
     * The small-capitals languages: the shipped set, then each language the file names applied on top.
     *
     * <p>Merging rather than replacing is the whole of it. A file that switches conversion on for one
     * language must not switch it off for every language it does not mention: that turns an operator adding
     * French into English silently losing its own writing, in every plugin at once.
     */
    private static Set<String> smallCaps(Map<Object, ? extends ConfigurationNode> languages) {
        Set<String> smallCaps = new HashSet<>(DEFAULT_SMALL_CAPS);
        for (Map.Entry<Object, ? extends ConfigurationNode> child : languages.entrySet()) {
            String language = String.valueOf(child.getKey()).toLowerCase(Locale.ROOT);
            if (child.getValue().getBoolean()) {
                smallCaps.add(language);
            } else {
                smallCaps.remove(language);
            }
        }
        return smallCaps;
    }

    /**
     * A colour written in the file: a name from the palette, or a hex code.
     *
     * <p>The palette is looked at first, so a server that calls a colour {@code sky} may write {@code sky}
     * everywhere and change the hex in one place.
     */
    private static TextColor resolve(String value, Map<String, TextColor> palette) {
        TextColor named = palette.get(value.toLowerCase(Locale.ROOT));
        return named != null ? named : parse(value);
    }

    private static TextColor parse(String hex) {
        TextColor parsed = TextColor.fromHexString(hex);
        if (parsed == null) {
            throw new IllegalArgumentException("not a colour and not a palette name: " + hex);
        }
        return parsed;
    }

    private static Map<String, TextColor> defaultRoles() {
        Map<String, TextColor> roles = new LinkedHashMap<>();
        roles.put(ACCENT, NamedTextColor.AQUA);
        roles.put(BODY, NamedTextColor.WHITE);
        roles.put("subtext", NamedTextColor.GRAY);
        roles.put("muted", NamedTextColor.GRAY);
        roles.put("dim", NamedTextColor.DARK_GRAY);
        roles.put("icon", NamedTextColor.GRAY);
        roles.put("crumb", NamedTextColor.DARK_GRAY);
        roles.put("value", NamedTextColor.YELLOW);
        roles.put("good", NamedTextColor.GREEN);
        roles.put("bad", NamedTextColor.RED);
        roles.put("warn", NamedTextColor.GOLD);
        roles.put("money", NamedTextColor.GOLD);
        roles.put("level", NamedTextColor.LIGHT_PURPLE);
        roles.put("cta", NamedTextColor.YELLOW);
        roles.put("info", NamedTextColor.AQUA);
        roles.put("rank", NamedTextColor.LIGHT_PURPLE);
        roles.put("event", NamedTextColor.BLUE);
        return Map.copyOf(roles);
    }
}

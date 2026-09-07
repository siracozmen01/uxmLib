package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.AbstractMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** What a menu file writes, turned into what a player reads. */
class CatalogueWordsTest {

    private static final String CATALOGUE = """
            menu {
              lore { description = "About", details = "Facts" }
              named = "<coins> coins"
              action { join = "<gold>Click</gold> to go there" }
              tile {
                title = "English"
                crumb = "Language"
                description = "Read the server in this language."
                action = "Click to read in it."
                state { label = "State", value = "On" }
              }
            }
            """;

    private PlayerMock viewer;
    private CatalogueWords words;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        Messages messages = new Messages(
                MessageCatalogLoader.fromNodes(Map.of(Locale.ENGLISH, parse(CATALOGUE)), Locale.ENGLISH),
                LocaleSource.ofDefault(Locale.ENGLISH));
        words = new CatalogueWords(messages, new Styler(Theme.defaults()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a tile line draws the blocks the catalogue holds")
    void aTileDrawsItsBlocks() {
        String drawn = plain(words.renderFor(viewer, "tile:5 @menu.tile state", Map.of()));

        assertThat(drawn)
                .contains("English")
                .contains("Language")
                .contains("State")
                .contains("On");
        assertThat(drawn).contains("Click to read in it.");
    }

    /**
     * The closing sentence of a tile that answers a different click in each state. The line names the
     * catalogue entry the sentence comes from, so each state is one line of the catalogue with its own
     * colour, and the tile itself is written once.
     *
     * <p>The colour is the whole of the point. A plugin that could not name the key rendered one of its
     * state sentences, stripped the markup off it and handed the bare words in as a value, because a value
     * reaches a catalogue line as text and never as markup. The sentence then arrived with no colour at all
     * and the word a player is meant to click on was painted like the words around it. So this asserts the
     * colour of that word and not only the words: an assertion on the words alone passes on the flat reading
     * too.
     */
    @Test
    @DisplayName("a tile line that names its own action key keeps that line's colour")
    void aNamedActionKeepsItsColour() {
        Component drawn = words.renderFor(viewer, "tile:5 @menu.tile state action:@menu.action.join", Map.of());

        assertThat(plain(drawn)).contains("Click to go there").doesNotContain("Click to read in it.");
        assertThat(colourOf(drawn, "Click")).isEqualTo(NamedTextColor.GOLD);
        assertThat(plain(drawn)).contains("English").contains("State").contains("On");
    }

    /**
     * The tile the estate already writes. A line that names no action key reads the one under its own block,
     * which is what keeps every menu file shipped so far drawing the tile it drew yesterday.
     */
    @Test
    @DisplayName("a tile line that names no action key reads the one under its own block")
    void anUnnamedActionReadsTheBlocksOwn() {
        String drawn = plain(words.renderFor(viewer, "tile:5 @menu.tile state", Map.of()));

        assertThat(drawn).contains("Click to read in it.").doesNotContain("Click to go there");
    }

    /** Leaving the block out wins over naming a key for it: a line that says both draws no sentence. */
    @Test
    @DisplayName("a line that names an action key and takes the block out draws no sentence")
    void takingTheBlockOutWinsOverNamingIt() {
        String drawn = plain(words.renderFor(viewer, "tile:5 @menu.tile -action action:@menu.action.join", Map.of()));

        assertThat(drawn).doesNotContain("Click to go there").doesNotContain("Click to read in it.");
        assertThat(drawn).contains("English");
    }

    /**
     * A key nobody translated leaves the sentence off rather than printing the path onto the tile. That is
     * what the block's own action key already does, and a named one answers the same way.
     */
    @Test
    @DisplayName("an action key the catalogue does not hold draws no sentence")
    void anUnknownActionKeyDrawsNothing() {
        String drawn = plain(words.renderFor(viewer, "tile:5 @menu.tile action:@menu.action.nothing", Map.of()));

        assertThat(drawn).doesNotContain("menu.action.nothing").doesNotContain("Click to read in it.");
        assertThat(drawn).contains("English");
    }

    /** The {@code @} is the estate's mark for a key and the tile key already takes it either way. So does this. */
    @Test
    @DisplayName("an action key reads the same with the key mark and without it")
    void theKeyMarkIsOptional() {
        assertThat(plain(words.renderFor(viewer, "tile:5 @menu.tile action:menu.action.join", Map.of())))
                .contains("Click to go there");
    }

    @Test
    @DisplayName("a block the line takes out is not drawn")
    void aBlockCanBeLeftOut() {
        assertThat(plain(words.renderFor(viewer, "tile:5 @menu.tile -action", Map.of())))
                .doesNotContain("Click to read in it.")
                .contains("English");
    }

    @Test
    @DisplayName("a line that names a key reads the catalogue, and one that does not is written as it stands")
    void aKeyIsReadAndAWordIsWritten() {
        assertThat(plain(words.text(viewer, "menu.tile.title", Map.of()))).isEqualTo("English");
        assertThat(plain(words.renderFor(viewer, "Ready", Map.of()))).isEqualTo("Ready");
    }

    @Test
    @DisplayName("a key nobody translated shows the key, so an operator sees what to write")
    void anUnknownKeyShowsItself() {
        assertThat(plain(words.text(viewer, "menu.nothing", Map.of()))).isEqualTo("menu.nothing");
    }

    /**
     * A line asked for with no viewer is the plain reading of it, which is what an inventory title and a
     * flattened label want.
     */
    @Test
    @DisplayName("a line rendered without a viewer is the plain reading of it")
    void aViewerlessLineIsThePlainReading() {
        assertThat(plain(words.render("Ready"))).isEqualTo("Ready");
    }

    /**
     * A tile asked for with no viewer is still a tile. Which language it reads in is the viewer's own answer and
     * there is nobody to ask, so it is drawn in the catalogue's own language. What it must never be is the line
     * the operator wrote: that is the mark on the item, in front of a player, as prose.
     */
    @Test
    @DisplayName("a tile rendered without a viewer is drawn and not written out")
    void aViewerlessTileIsStillDrawn() {
        String drawn = plain(words.render("tile:5 @menu.tile state"));

        assertThat(drawn).doesNotContain("tile:").doesNotContain("@menu.tile");
        assertThat(drawn).contains("English").contains("State").contains("On");
    }

    /**
     * The other spelling. A menu file that writes {@code @tile:} hands the mark in as a key, because the leading
     * {@code @} was for a while the only way a tile line could reach a viewer at all. It draws the same tile.
     */
    @Test
    @DisplayName("a key that carries the tile mark draws the tile")
    void aKeyedTileIsDrawn() {
        String drawn = plain(words.text(viewer, "tile:5 @menu.tile state", Map.of()));

        assertThat(drawn).doesNotContain("tile:");
        assertThat(drawn).contains("English").contains("State");
    }

    /**
     * A catalogue line names the values it wants and the map answers them one at a time. The map the menu engine
     * hands over is exactly this shape: it holds the {@code %token%}s the written line spells, which for a tile
     * and for a {@code @key} line is none of them, and it answers any other id when something asks by name. A
     * walk of it therefore finds nothing to offer, and a walk is what this used to build its resolvers from.
     */
    @Test
    @DisplayName("a catalogue line is given the value it asks for by name")
    void aCatalogueLineIsAnsweredByName() {
        assertThat(plain(words.text(viewer, "menu.named", asked(Map.of("coins", "12")))))
                .isEqualTo("12 coins");
    }

    /** A map that holds nothing to walk and answers when it is asked, which is what the engine hands over. */
    private static Map<String, String> asked(Map<String, String> values) {
        return new AbstractMap<>() {

            @Override
            public @Nullable String get(Object key) {
                return values.get(key);
            }

            @Override
            public Set<Entry<String, String>> entrySet() {
                return Set.of();
            }
        };
    }

    /**
     * The colour the viewer reads {@code word} in, with the colours of the blocks above it inherited the way
     * a client inherits them. A word with no colour of its own therefore answers with the colour the tile
     * paints the sentence in, which is the flat reading this test set exists to tell apart from a coloured one.
     */
    private static @Nullable TextColor colourOf(Component text, String word) {
        return colourOf(text, word, null);
    }

    private static @Nullable TextColor colourOf(Component text, String word, @Nullable TextColor inherited) {
        TextColor colour = text.color() != null ? text.color() : inherited;
        if (text instanceof TextComponent written && written.content().contains(word)) {
            return colour;
        }
        for (Component child : text.children()) {
            TextColor found = colourOf(child, word, colour);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static org.spongepowered.configurate.ConfigurationNode parse(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load();
    }
}

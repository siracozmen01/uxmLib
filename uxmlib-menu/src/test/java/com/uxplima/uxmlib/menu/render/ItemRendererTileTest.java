package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.CatalogueWords;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A tile line, written the way a menu file writes one and read the way a player reads one.
 *
 * <p>Every test here goes in through {@link ItemRenderer#lore} or {@link ItemRenderer#render}, which is the
 * call the engine makes, and out through the words on the item. Nothing calls the drawing method directly. A
 * test that did would have passed while the shipped menus showed the mark as prose, because the defect was
 * never in the drawing: it was in which of the catalogue's two methods the renderer handed the line to.
 */
class ItemRendererTileTest {

    /** A catalogue in the shape a tile reads: the two words over the blocks, and one block of a shop offer. */
    private static final String CATALOGUE = """
            menu {
              lore { description = "About", details = "Facts" }
              offer {
                title = "<coins> coins"
                crumb = "Player shop"
                description = "What this seller asks for it."
                action = "Click to buy one."
                stock { label = "Stock", value = "7" }
              }
            }
            """;

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private ItemRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        placeholders.register("coins", ctx -> "12");
        Messages messages = new Messages(
                MessageCatalogLoader.fromNodes(Map.of(Locale.ENGLISH, parse(CATALOGUE)), Locale.ENGLISH),
                LocaleSource.ofDefault(Locale.ENGLISH));
        renderer = new ItemRenderer(
                new CatalogueWords(messages, new Styler(Theme.defaults())), Theme::defaults, placeholders);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a tile line is drawn as a tile and never as its own characters")
    void aTileLineIsDrawnAsATile() {
        String drawn = lore("tile:5 @menu.offer stock");

        assertThat(drawn).doesNotContain("tile:").doesNotContain("@menu.offer");
        assertThat(drawn)
                .contains("Player shop")
                .contains("Stock")
                .contains("7")
                .contains("Click to buy one.");
    }

    /**
     * The same line again, read off the finished item rather than off the list of components. This is the
     * player's own view: the builder splits the tile into the lines the client draws, so what the tooltip
     * says is what these strings say.
     */
    @Test
    @DisplayName("the tooltip a player reads holds no tile mark")
    void thePlayerReadsNoMark() {
        List<String> tooltip = tooltip("tile:5 @menu.offer stock");

        assertThat(tooltip).isNotEmpty();
        assertThat(tooltip).noneMatch(line -> line.contains("tile:"));
        assertThat(tooltip).hasSizeGreaterThan(1);
        assertThat(String.join("\n", tooltip)).contains("Player shop");
    }

    /**
     * The other spelling of the same line. Two plugins write {@code @tile:} because the leading {@code @} was
     * the only way to reach the viewer, and they must keep working now that the bare form does too.
     */
    @Test
    @DisplayName("the @tile: spelling draws the same tile")
    void theKeyedSpellingDrawsATile() {
        String drawn = lore("@tile:5 @menu.offer stock");

        assertThat(drawn).doesNotContain("tile:");
        assertThat(drawn).contains("Player shop").contains("Stock");
    }

    /**
     * A tile names its values inside the catalogue and not on the line, so the line spells no {@code %token%}
     * at all. The values still have to arrive: the engine hands over a map that answers a name when it is
     * asked for one, and the catalogue is what asks.
     */
    @Test
    @DisplayName("a value the catalogue asks for by name reaches the tile")
    void aTileReadsAValueTheCatalogueNames() {
        assertThat(lore("tile:5 @menu.offer stock")).contains("12 coins").doesNotContain("<coins>");
    }

    /** The same question on the plainer path: a {@code @key} line whose catalogue entry names a value. */
    @Test
    @DisplayName("a value the catalogue asks for by name reaches a @key line")
    void aKeyLineReadsAValueTheCatalogueNames() {
        assertThat(lore("@menu.offer.title")).isEqualTo("12 coins");
    }

    /** A line that is neither is untouched by any of this. */
    @Test
    @DisplayName("an ordinary line is still written as it stands")
    void anOrdinaryLineIsUnchanged() {
        assertThat(lore("Ready")).isEqualTo("Ready");
    }

    /** The rendered lore of an item whose single lore entry is {@code raw}, flattened to one string. */
    private String lore(String raw) {
        List<Component> lines = renderer.lore(
                item("material = STONE, name = \"n\", lore = [\"" + raw + "\"]"), MenuContext.of(viewer, null, 0));
        return lines.stream().map(ItemRendererTileTest::plain).collect(Collectors.joining("\n"));
    }

    /** The lines of the tooltip the client draws for that item, which is where the builder's split shows. */
    private List<String> tooltip(String raw) {
        ItemStack stack = renderer.render(
                item("material = STONE, name = \"n\", lore = [\"" + raw + "\"]"), MenuContext.of(viewer, null, 0));
        List<Component> lines = Objects.requireNonNull(stack.getItemMeta()).lore();
        return Objects.requireNonNull(lines).stream()
                .map(ItemRendererTileTest::plain)
                .toList();
    }

    private static MenuItemSpec item(String hocon) {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { one { slot = 0, " + hocon + " } }");
        return Objects.requireNonNull(spec.items().get("one"));
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static ConfigurationNode parse(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(hocon)))
                .build()
                .load();
    }
}

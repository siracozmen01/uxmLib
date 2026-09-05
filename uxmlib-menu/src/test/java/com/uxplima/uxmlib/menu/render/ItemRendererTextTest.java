package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * How an operator's written line becomes the words a viewer reads. Every token is substituted first, then a line
 * beginning with an at sign is looked up in the viewer's catalogue and anything else is rendered as written. The
 * renderer decides no wording of its own: it only decides what a token means and in which order.
 */
class ItemRendererTextTest {

    /** A catalogue that reports what it was asked for, so a test can tell the two resolution paths apart. */
    private static final class RecordingText implements GuiText {

        private final List<String> keysAsked = new ArrayList<>();

        private final List<Map<String, String>> argumentsAsked = new ArrayList<>();

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            keysAsked.add(key);
            argumentsAsked.add(placeholders);
            return Component.text("catalogue(" + key + ")");
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    private final RecordingText catalogue = new RecordingText();

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private ItemRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        renderer = new ItemRenderer(catalogue, Theme::defaults, placeholders);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MenuContext ctx() {
        return MenuContext.of(viewer, null, 0);
    }

    private static String flat(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static MenuItemSpec item(String hocon) {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { one { slot = 0, " + hocon + " } }");
        return java.util.Objects.requireNonNull(spec.items().get("one"));
    }

    @Test
    void aRegisteredTokenBecomesItsValue() {
        placeholders.register("warp_name", ctx -> "spawn");

        assertThat(flat(renderer.title("Warp: %warp_name%", ctx()))).isEqualTo("Warp: spawn");
    }

    @Test
    void aTokenNobodyRegisteredResolvesToNothingRatherThanShowingItsOwnName() {
        assertThat(flat(renderer.title("Warp: %warp_name%", ctx())))
                .as("a player reading a raw %token% learns nothing and reads as a broken menu")
                .isEqualTo("Warp: ");
    }

    @Test
    void anArgumentTokenReadsWhatTheMenuWasOpenedWith() {
        MenuContext opened = MenuContext.of(viewer, null, 0, Map.of("amount", "5"));

        assertThat(flat(renderer.title("give %argument_amount%", opened))).isEqualTo("give 5");
        assertThat(flat(renderer.title("give %argument_missing%", opened))).isEqualTo("give ");
    }

    @Test
    void aMenusOwnTokenWinsOverARegisteredOneForThatMenuAlone() {
        placeholders.register("label", ctx -> "the shared word");
        MenuContext local = ctx().withLocalPlaceholders(Map.of("label", "this menu's word"));

        assertThat(flat(renderer.title("%label%", local))).isEqualTo("this menu's word");
        assertThat(flat(renderer.title("%label%", ctx())))
                .as("the override is scoped to the menu that declared it")
                .isEqualTo("the shared word");
    }

    @Test
    void aMenusOwnTokenIsItselfResolvedSoOneCanBeWrittenInTermsOfAnother() {
        placeholders.register("world", ctx -> "nether");
        MenuContext local = ctx().withLocalPlaceholders(Map.of("where", "in the %world%"));

        assertThat(flat(renderer.title("%where%", local))).isEqualTo("in the nether");
    }

    @Test
    void aTokenThatNamesItselfStopsRatherThanRecursingForever() {
        MenuContext local = ctx().withLocalPlaceholders(Map.of("loop", "a %loop%"));

        assertThat(flat(renderer.title("%loop%", local)))
                .as("an operator typo must cost a strange line, never the server")
                .startsWith("a a a");
    }

    /**
     * A registered placeholder's value is written into the line and not read again. Only a menu-local template is
     * expanded further, which is the one an operator wrote in the file and can be asked to fix. A registered value
     * comes from another plugin's data (a player's own nickname, a warp's own description) and re-scanning it would
     * let whatever a player typed into that field name any token in the menu.
     *
     * <p>The single pass is {@link java.util.regex.Matcher#appendReplacement}'s behaviour rather than this class's, so
     * it is pinned here: a rewrite that resolved the tokens itself would lose the guarantee without failing anything.
     */
    @Test
    void aRegisteredValueCarryingATokenIsNotItselfExpanded() {
        placeholders.register("secret", ctx -> "hidden");
        placeholders.register("nickname", ctx -> "%secret%");

        assertThat(flat(renderer.title("hello %nickname%", ctx())))
                .as("a value that arrives from outside the file is text, never more tokens")
                .isEqualTo("hello %secret%");
    }

    /**
     * A dollar sign in a placeholder value is a character, not a group reference. This is what {@link
     * java.util.regex.Matcher#quoteReplacement} buys, and without it a price written {@code $1} would either vanish or
     * throw out of the render.
     */
    @Test
    void aDollarSignInAValueIsACharacterRatherThanAGroupReference() {
        placeholders.register("price", ctx -> "$1.50");

        assertThat(flat(renderer.title("costs %price%", ctx()))).isEqualTo("costs $1.50");
    }

    @Test
    void anAtKeyIsLookedUpInTheViewersCatalogue() {
        assertThat(flat(renderer.title("@menu.warps.title", ctx()))).isEqualTo("catalogue(menu.warps.title)");
        assertThat(catalogue.keysAsked).containsExactly("menu.warps.title");
    }

    @Test
    void anAtKeyMayCarryATokenInTheKeyItself() {
        placeholders.register("kind", ctx -> "server");

        renderer.title("@menu.%kind%.title", ctx());

        assertThat(catalogue.keysAsked).containsExactly("menu.server.title");
    }

    @Test
    void anAtKeysArgumentsComeFromTheSamePlaceholdersATokenWouldUse() {
        placeholders.register("warp", ctx -> "spawn");

        renderer.title("@menu.warps.line", ctx());

        assertThat(catalogue.argumentsAsked)
                .as("a catalogue line's {warp} must fill from the same source as a %warp% token")
                .containsExactly(Map.of("warp", "spawn"));
    }

    @Test
    void aLineThatIsNotAKeyIsRenderedAsWritten() {
        renderer.title("<red>plain words", ctx());

        assertThat(catalogue.keysAsked)
                .as("only an at-key reaches the catalogue; everything else is the operator's own text")
                .isEmpty();
    }

    @Test
    void anEmptyLineStaysEmptyRatherThanBecomingACatalogueMiss() {
        assertThat(renderer.title("", ctx())).isEqualTo(Component.empty());
    }

    @Test
    void aMathBlockIsEvaluatedAfterItsTokensAreSubstituted() {
        placeholders.register("count", ctx -> "4");

        assertThat(flat(renderer.title("total {math: %count% * 3}", ctx()))).isEqualTo("total 12");
    }

    @Test
    void aMalformedMathBlockRendersBlankRatherThanLeakingTheExpression() {
        assertThat(flat(renderer.title("total {math: not a sum}", ctx())))
                .as("a player must never read the operator's broken expression back")
                .isEqualTo("total ");
    }

    @Test
    void aMaterialSpecHasItsTokensExpandedSoAnIconCanBePerEntry() {
        placeholders.register("player", ctx -> "Notch");

        assertThat(renderer.materialSpec(item("material = \"skull:%player%\""), ctx()))
                .as("the line around the token has to survive, or no provider claims what is left")
                .isEqualTo("skull:Notch");
    }

    @Test
    void aMaterialThatIsOneWholeTokenStillResolvesToWhateverThatTokenNames() {
        placeholders.register("head", ctx -> "skull:Notch");

        assertThat(renderer.materialSpec(item("material = \"%head%\""), ctx())).isEqualTo("skull:Notch");
    }

    @Test
    void aLiteralMaterialReachesTheMaterialLookupUntouched() {
        assertThat(renderer.materialSpec(item("material = DIAMOND"), ctx())).isEqualTo("DIAMOND");
    }

    @Test
    void aFormButtonReadsTheNameThenEachLoreLineOnItsOwnLine() {
        placeholders.register("warp_name", ctx -> "spawn");

        assertThat(renderer.buttonText(
                        item("material = STONE, name = \"%warp_name%\", lore = [\"a\", \"\", \"b\"]"), ctx()))
                .as("a blank spec lore line stays a blank line, so the operator's spacing carries over")
                .isEqualTo("spawn\na\n\nb");
    }

    @Test
    void aSharedLabelGoesThroughTheCatalogueSoItHonoursTheViewersLanguage() {
        assertThat(renderer.plainMessage(viewer, "gui.confirm.yes")).isEqualTo("catalogue(gui.confirm.yes)");
    }
}

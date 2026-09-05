package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.item.Tooltips;
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
 * The stack an operator's item block builds. Almost every decision here is fail-soft on purpose: a spec is written by
 * hand in a file, so a typo has to cost one wrong-looking icon rather than a menu that will not open. These tests are
 * about which wrong value is tolerated and what it degrades to.
 *
 * <p>The {@code hidden-components} branch of {@code hiddenFor} is deliberately not covered, and not because it is
 * awkward. It reads the data component registry through {@code fromRegistry}, which catches {@link RuntimeException}.
 * MockBukkit's {@code UnimplementedOperationException} is a {@code TestAbortedException}, which is a
 * {@code RuntimeException}, so an unimplemented registry call there is swallowed and handed back as an empty optional:
 * the test does not abort and does not skip, it <em>passes</em>. {@code verifyNoAbortedTests} cannot see it either,
 * because nothing was ever recorded as a skip. A green test on that branch would not be evidence about the branch.
 * Covering it means first pinning, separately, that the registry answers at all.
 */
class ItemRendererIconTest {

    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text("catalogue(" + key + ")");
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private ItemRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        renderer = new ItemRenderer(new PlainText(), Theme::defaults, placeholders);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MenuContext ctx() {
        return MenuContext.of(viewer, null, 0);
    }

    private static MenuItemSpec item(String hocon) {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { one { slot = 0, " + hocon + " } }");
        return Objects.requireNonNull(spec.items().get("one"));
    }

    private ItemStack render(String hocon) {
        return renderer.render(item(hocon), ctx());
    }

    private static boolean glint(ItemStack stack) {
        return Boolean.TRUE.equals(Objects.requireNonNull(stack.getItemMeta()).getEnchantmentGlintOverride());
    }

    private static List<String> flatLore(ItemStack stack) {
        List<Component> lore = stack.lore();
        return lore == null
                ? List.of()
                : lore.stream()
                        .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                        .toList();
    }

    // -- the material -----------------------------------------------------------------------------------------

    @Test
    void aNamedMaterialIsTheStacksMaterial() {
        assertThat(render("material = DIAMOND_SWORD, name = \"n\"").getType()).isEqualTo(Material.DIAMOND_SWORD);
    }

    /** A typo costs one stone icon in one slot. Aborting the render would cost the whole menu. */
    @Test
    void anUnknownMaterialFallsBackToStoneRatherThanAbortingTheRender() {
        assertThat(render("material = NOT_A_REAL_BLOCK, name = \"n\"").getType())
                .isEqualTo(Material.STONE);
    }

    @Test
    void aProviderShapedSpecNoProviderClaimedIsStoneTooRatherThanAThrow() {
        assertThat(render("material = \"skull:\", name = \"n\"").getType()).isEqualTo(Material.STONE);
    }

    // -- the amount -------------------------------------------------------------------------------------------

    @Test
    void aStaticAmountReachesTheStack() {
        assertThat(render("material = STONE, name = \"n\", decor { amount = 5 }")
                        .getAmount())
                .isEqualTo(5);
    }

    @Test
    void aDynamicAmountIsResolvedPerRenderAndBeatsTheStaticValue() {
        placeholders.register("count", ctx -> "7");

        assertThat(render("material = STONE, name = \"n\", decor { amount = \"%count%\" }")
                        .getAmount())
                .isEqualTo(7);
    }

    /** A token that resolves to nothing is a stack of one, because a stack of zero is not an item. */
    @Test
    void anAmountBelowOneIsFlooredRatherThanRenderingNothing() {
        placeholders.register("count", ctx -> "0");

        assertThat(render("material = STONE, name = \"n\", decor { amount = \"%count%\" }")
                        .getAmount())
                .isEqualTo(1);
    }

    @Test
    void anAmountAboveTheMaterialsStackSizeKeepsOneRatherThanAborting() {
        placeholders.register("count", ctx -> "900");

        assertThat(render("material = DIAMOND_SWORD, name = \"n\", decor { amount = \"%count%\" }")
                        .getAmount())
                .isEqualTo(1);
    }

    @Test
    void anAmountTokenThatIsNotANumberLeavesTheStaticValueStanding() {
        placeholders.register("count", ctx -> "many");

        assertThat(render("material = STONE, name = \"n\", decor { amount = \"%count%\" }")
                        .getAmount())
                .as("an unparseable dynamic amount falls back to the spec's own, which defaults to one")
                .isEqualTo(1);
    }

    // -- the glint --------------------------------------------------------------------------------------------

    @Test
    void aDynamicGlowIsOnForTheThreeWaysAFileSaysYes() {
        placeholders.register("chosen", ctx -> "true");
        placeholders.register("picked", ctx -> "yes");
        placeholders.register("flagged", ctx -> "1");

        for (String token : List.of("%chosen%", "%picked%", "%flagged%")) {
            assertThat(glint(render("material = STONE, name = \"n\", decor { glow = \"" + token + "\" }")))
                    .as("a list template glints only on the entry a screen calls selected: " + token)
                    .isTrue();
        }
    }

    @Test
    void aDynamicGlowIsOffForAnythingElse() {
        placeholders.register("chosen", ctx -> "no");
        placeholders.register("empty", ctx -> "");

        assertThat(glint(render("material = STONE, name = \"n\", decor { glow = \"%chosen%\" }")))
                .isFalse();
        assertThat(glint(render("material = STONE, name = \"n\", decor { glow = \"%empty%\" }")))
                .as("a token that resolves to nothing is not a yes")
                .isFalse();
    }

    @Test
    void aStaticGlowStandsWhenThereIsNoTokenToResolve() {
        assertThat(glint(render("material = STONE, name = \"n\", decor { glow = true }")))
                .isTrue();
    }

    /**
     * The glint is written as an override rather than left unset, so an icon built from a base stack that already
     * glints (a serialized enchanted item) can be told not to. A spec saying no has to mean no.
     */
    @Test
    void aSpecThatSaysNoGlowSaysSoOutLoudRatherThanStayingSilent() {
        ItemStack stack = render("material = STONE, name = \"n\", decor { glow = false }");

        assertThat(Objects.requireNonNull(stack.getItemMeta()).hasEnchantmentGlintOverride())
                .isTrue();
        assertThat(glint(stack)).isFalse();
    }

    // -- the lore ---------------------------------------------------------------------------------------------

    /**
     * One placeholder emitting several lines is how a per-entry icon shows a variable number of requirement ticks
     * without the spec having to declare a fixed line count it cannot know.
     */
    @Test
    void aPlaceholderValueCarryingNewlinesBecomesOneLoreLinePerSegment() {
        placeholders.register("requirements", ctx -> "yes\nno\nmaybe");

        assertThat(flatLore(render("material = STONE, name = \"\", lore = [\"%requirements%\"]")))
                .containsExactly("yes", "no", "maybe");
    }

    @Test
    void aCatalogueLineStaysOneComponentBecauseTheCatalogueOwnsItsOwnLayout() {
        assertThat(flatLore(render("material = STONE, name = \"\", lore = [\"@menu.warps.line\"]")))
                .containsExactly("catalogue(menu.warps.line)");
    }

    @Test
    void aBlankSpecLineStaysOneBlankLine() {
        assertThat(flatLore(render("material = STONE, name = \"\", lore = [\"a\", \"\", \"b\"]")))
                .containsExactly("a", "", "b");
    }

    // -- the mark ---------------------------------------------------------------------------------------------

    /**
     * Every tile the engine renders carries the mark, so a display copy that escapes into a real inventory can be
     * stripped again. The mark rides on the copy; the viewer's own items are never touched here.
     */
    @Test
    void everyRenderedTileIsMarkedSoAnEscapedCopyIsStrippable() {
        assertThat(MenuItemMark.isMarked(render("material = STONE, name = \"n\"")))
                .isTrue();
    }

    // -- what the tooltip silences ----------------------------------------------------------------------------

    /** A spec that says show me the vanilla lines gets them all, so nothing is hidden. */
    @Test
    void aSpecThatAsksForTheVanillaTooltipHidesNothing() {
        MenuItemSpec spec = item("material = DIAMOND_SWORD, name = \"n\", decor { hide-vanilla-tooltip = false }");

        assertThat(ItemRenderer.hiddenFor(spec.decor().meta())).isEmpty();
    }

    /** Silence is the default, because a menu tile is a button and a button's tooltip is what the operator wrote. */
    @Test
    void aPlainSpecSilencesTheWholeVanillaSet() {
        MenuItemSpec spec = item("material = DIAMOND_SWORD, name = \"n\"");

        assertThat(ItemRenderer.hiddenFor(spec.decor().meta()))
                .containsExactlyInAnyOrderElementsOf(Tooltips.VANILLA_COMPONENTS);
    }

    /**
     * A component the operator filled in themselves is content rather than noise, so its line survives. This is the
     * whole reason the hidden set is computed rather than fixed: hiding an enchantment the spec asked for would hide
     * the thing the spec was written to show.
     */
    @Test
    void aComponentTheOperatorDeclaredKeepsItsLine() {
        MenuItemSpec spec = item("material = DIAMOND_SWORD, name = \"n\", decor { enchantments = [\"sharpness:5\"] }");

        Set<DataComponentType> hidden = ItemRenderer.hiddenFor(spec.decor().meta());

        assertThat(hidden).doesNotContain(DataComponentTypes.ENCHANTMENTS);
        assertThat(hidden)
                .as("only the declared one is spared; the rest of the vanilla noise still goes")
                .containsExactlyInAnyOrderElementsOf(
                        minus(Tooltips.VANILLA_COMPONENTS, DataComponentTypes.ENCHANTMENTS));
    }

    @Test
    void unbreakableIsDeclaredContentTooAndSoIsALeatherColour() {
        MenuItemSpec unbreakable = item("material = DIAMOND_SWORD, name = \"n\", decor { unbreakable = true }");
        MenuItemSpec dyed = item("material = LEATHER_CHESTPLATE, name = \"n\", decor { leather-color = \"#A1FF33\" }");

        assertThat(ItemRenderer.hiddenFor(unbreakable.decor().meta())).doesNotContain(DataComponentTypes.UNBREAKABLE);
        assertThat(ItemRenderer.hiddenFor(dyed.decor().meta())).doesNotContain(DataComponentTypes.DYED_COLOR);
    }

    private static Set<DataComponentType> minus(Set<DataComponentType> all, DataComponentType removed) {
        Set<DataComponentType> rest = new HashSet<>(all);
        rest.remove(removed);
        return rest;
    }
}

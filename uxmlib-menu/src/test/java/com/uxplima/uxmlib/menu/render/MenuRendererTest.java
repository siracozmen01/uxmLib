package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ContentProviderRegistry;
import com.uxplima.uxmlib.menu.binding.MenuPlaceholders;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.providers.ContentProvider;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.runtime.PagedListView;
import com.uxplima.uxmlib.menu.spec.ContentRegionSpec;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * What reaches the window, and in what order. The renderer draws three layers into one inventory: the static items a
 * spec declares, the list cells that page over their own slots, and the content regions a feature owns. Each layer
 * decides what the one under it may keep, and the tests here are about those decisions rather than about how an
 * individual icon is worded.
 */
class MenuRendererTest {

    /** A catalogue that hands every key straight back, so a rendered name is readable in an assertion. */
    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text(key);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** A provider that paints a fixed list of stacks and reports whether it was asked to. */
    private static final class FixedProvider implements ContentProvider {

        private final List<@Nullable ItemStack> painted;

        private final boolean repaints;

        private int renders;

        FixedProvider(List<@Nullable ItemStack> painted, boolean repaints) {
            this.painted = painted;
            this.repaints = repaints;
        }

        @Override
        public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
            renders++;
            return painted;
        }

        @Override
        public boolean repaintsOnRedraw() {
            return repaints;
        }
    }

    private final ConditionRegistry conditions = new ConditionRegistry();

    private final ContentProviderRegistry contents = new ContentProviderRegistry();

    private final Map<Integer, RenderedSlot> routed = new LinkedHashMap<>();

    private MenuRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        ItemRenderer items = new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry());
        renderer = new MenuRenderer(items, conditions, contents);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MenuContext ctx() {
        return MenuContext.of(viewer, null, 0);
    }

    private static MenuSpec spec(String hocon) {
        return new MenuSpecLoader().parse(hocon);
    }

    private static Inventory inv(int size) {
        return Bukkit.createInventory(null, size);
    }

    private static @Nullable Material materialAt(Inventory inv, int slot) {
        ItemStack stack = inv.getItem(slot);
        return stack == null ? null : stack.getType();
    }

    private void populate(Inventory inv, MenuSpec spec, Map<String, List<?>> lists) {
        renderer.populate(inv, spec, ctx(), routed::put, lists);
    }

    // -- the page indicator -----------------------------------------------------------------------------------

    /**
     * A static item's page tokens count the same list the arrows turn. The engine used to read "the first list-backed
     * item" out of the spec's item map in three separate places, and that map's order is the config library's rather
     * than the file's, so a menu carrying two lists could turn one of them and count the pages of the other inside
     * one render.
     */
    @Test
    void theStaticPageIndicatorCountsTheListDrawnNearestTheStartOfTheWindow() {
        PlaceholderRegistry tokens = new PlaceholderRegistry();
        MenuPlaceholders.registerPaging(tokens);
        MenuRenderer counting =
                new MenuRenderer(new ItemRenderer(new PlainText(), Theme::defaults, tokens), conditions, contents);
        MenuSpec spec = spec(
                """
                rows = 2
                items {
                  kits { slots = [4, 5], list { source = kits, template { material = CHEST, name = "kit" } } }
                  warps { slots = [0, 1], list { source = warps, template { material = PAPER, name = "warp" } } }
                  label { slot = 8, material = PAPER, name = "%page%/%max_page%" }
                }
                """);
        MenuContext ctx = MenuContext.of(viewer, null, 1)
                .withPagedViews(Map.of("warps", new PagedListView(1, 5, 2), "kits", new PagedListView(0, 100, 2)));
        Inventory inv = inv(18);

        counting.populate(
                inv, spec, ctx, routed::put, Map.of("warps", List.of("c", "d"), "kits", List.of("stone", "iron")));

        assertThat(nameAt(inv, 8))
                .as("five warps over pages of two, and the hundred kits drawn later in the window are not counted")
                .isEqualTo("2/3");
    }

    /** A menu that pages nothing still reports one page, so an indicator on it reads "1/1" rather than "1/0". */
    @Test
    void aMenuWithNoListReportsASinglePage() {
        PlaceholderRegistry tokens = new PlaceholderRegistry();
        MenuPlaceholders.registerPaging(tokens);
        MenuRenderer counting =
                new MenuRenderer(new ItemRenderer(new PlainText(), Theme::defaults, tokens), conditions, contents);
        MenuSpec spec = spec(
                """
                rows = 1
                items {
                  label { slot = 0, material = PAPER, name = "%page%/%max_page%" }
                }
                """);
        Inventory inv = inv(9);

        counting.populate(inv, spec, ctx(), routed::put, Map.of());

        assertThat(nameAt(inv, 0)).isEqualTo("1/1");
    }

    /** The plain-text name of the stack at {@code slot}, so an expanded token is readable in an assertion. */
    private static String nameAt(Inventory inv, int slot) {
        ItemStack stack = Objects.requireNonNull(inv.getItem(slot), "no stack at slot " + slot);
        return PlainTextComponentSerializer.plainText()
                .serialize(Objects.requireNonNull(stack.getItemMeta().displayName(), "no display name"));
    }

    // -- the Bedrock button list ------------------------------------------------------------------------------

    @Test
    void theFormButtonsComeOutInSlotOrderRatherThanDeclarationOrder() {
        MenuSpec spec = spec(
                """
                rows = 1
                items {
                  last { slot = 8, material = STONE, name = "eight", click { left = ["close"] } }
                  first { slot = 1, material = STONE, name = "one", click { left = ["close"] } }
                }
                """);

        assertThat(renderer.visibleStaticItemsInSlotOrder(spec, ctx()))
                .extracting(MenuItemSpec::name)
                .containsExactly("one", "eight");
    }

    /** A tap that does nothing is not a button, so the decorative half of a menu does not reach the form. */
    @Test
    void anItemWithNoClickActionIsNotAFormButton() {
        MenuSpec spec = spec(
                """
                rows = 1
                items {
                  filler { slot = 0, material = GRAY_STAINED_GLASS_PANE, name = "pane" }
                  go { slot = 1, material = STONE, name = "go", click { left = ["close"] } }
                }
                """);

        assertThat(renderer.visibleStaticItemsInSlotOrder(spec, ctx()))
                .extracting(MenuItemSpec::name)
                .as("a form button that does nothing on tap is worse than no button")
                .containsExactly("go");
    }

    @Test
    void aListBackedItemIsNotAStaticButton() {
        MenuSpec spec = spec(
                """
                rows = 1
                items {
                  go { slot = 0, material = STONE, name = "go", click { left = ["close"] } }
                  grid { slots = [1, 2], list { source = "warps:all", template { material = PAPER, click { left = ["close"] } } } }
                }
                """);

        assertThat(renderer.visibleStaticItemsInSlotOrder(spec, ctx()))
                .extracting(MenuItemSpec::name)
                .as("the form path pages a list's own entries separately, after the static buttons")
                .containsExactly("go");
    }

    @Test
    void anItemWhoseViewFailsIsNotOfferedToABedrockViewer() {
        conditions.register("never", (ctx, args) -> false);
        MenuSpec spec = spec(
                """
                rows = 1
                items {
                  hidden { slot = 0, material = STONE, name = "hidden", view = ["never"], click { left = ["close"] } }
                  shown { slot = 1, material = STONE, name = "shown", click { left = ["close"] } }
                }
                """);

        assertThat(renderer.visibleStaticItemsInSlotOrder(spec, ctx()))
                .extracting(MenuItemSpec::name)
                .containsExactly("shown");
    }

    /** A wiring gap must hide the item rather than show it, because the wrong answer here is the one that leaks. */
    @Test
    void anUnregisteredConditionHidesTheItemRatherThanShowingIt() {
        MenuSpec spec = spec(
                """
                rows = 1
                items { gated { slot = 0, material = STONE, name = "gated", view = ["nobody-wired-this"], click { left = ["close"] } } }
                """);

        assertThat(renderer.visibleStaticItemsInSlotOrder(spec, ctx())).isEmpty();
    }

    @Test
    void anInvertedRequirementPassesWhenItsConditionDoesNot() {
        conditions.register("banned", (ctx, args) -> false);
        MenuSpec spec = spec(
                """
                rows = 1
                items { go { slot = 0, material = STONE, name = "go", view = ["!banned"], click { left = ["close"] } } }
                """);

        assertThat(renderer.visibleStaticItemsInSlotOrder(spec, ctx()))
                .extracting(MenuItemSpec::name)
                .containsExactly("go");
    }

    // -- the static layer -------------------------------------------------------------------------------------

    @Test
    void aStaticItemIsPaintedInItsSlotAndRoutedFromThere() {
        Inventory inv = inv(27);

        populate(inv, spec("rows = 3\nitems { go { slot = 4, material = DIAMOND, name = \"go\" } }"), Map.of());

        assertThat(materialAt(inv, 4)).isEqualTo(Material.DIAMOND);
        assertThat(routed.keySet()).containsExactly(4);
        assertThat(Objects.requireNonNull(routed.get(4)).item().name()).isEqualTo("go");
        assertThat(Objects.requireNonNull(routed.get(4)).entry())
                .as("a static item is not an entry of anything")
                .isNull();
    }

    /**
     * A slot a validated spec declares always fits a chest, so this only bites on a smaller window. It is skipped
     * rather than thrown on, because an out-of-bounds write would blank the whole menu over one misplaced item.
     */
    @Test
    void aSlotPastTheEndOfASmallerWindowIsSkippedRatherThanThrown() {
        Inventory inv = inv(9);
        MenuSpec spec = spec(
                """
                rows = 3
                items {
                  inside { slot = 0, material = DIAMOND, name = "in" }
                  outside { slot = 20, material = STONE, name = "out" }
                }
                """);

        populate(inv, spec, Map.of());

        assertThat(materialAt(inv, 0)).isEqualTo(Material.DIAMOND);
        assertThat(routed.keySet())
                .as("a slot the window cannot address is not routed either")
                .containsExactly(0);
    }

    // -- the list layer ---------------------------------------------------------------------------------------

    @Test
    void aListStampsItsTemplateOncePerEntryAndRoutesEachWithThatEntry() {
        Inventory inv = inv(27);
        MenuSpec spec = spec(
                """
                rows = 3
                items { grid { slots = [0, 1, 2], list { source = "warps:all", template { material = PAPER } } } }
                """);

        populate(inv, spec, Map.of("warps:all", List.of("spawn", "shop")));

        assertThat(materialAt(inv, 0)).isEqualTo(Material.PAPER);
        assertThat(materialAt(inv, 1)).isEqualTo(Material.PAPER);
        assertThat(routed.keySet()).containsExactly(0, 1);
        assertThat(routed.values()).extracting(RenderedSlot::entry).containsExactly("spawn", "shop");
    }

    /** A page flip to a shorter page must not leave the longer page's tail behind. */
    @Test
    void aContentSlotTheListDoesNotFillIsClearedRatherThanLeftStale() {
        Inventory inv = inv(27);
        inv.setItem(2, new ItemStack(Material.BEDROCK));
        MenuSpec spec = spec(
                """
                rows = 3
                items { grid { slots = [0, 1, 2], list { source = "warps:all", template { material = PAPER } } } }
                """);

        populate(inv, spec, Map.of("warps:all", List.of("spawn")));

        assertThat(materialAt(inv, 2)).isNull();
    }

    /**
     * A frame an author deliberately layered over the backdrop shows through where the list runs short, and the plain
     * backdrop does not. The rule is priority: an item placed above the base layer is decoration the author asked for,
     * and the base layer is what a list exists to replace.
     */
    @Test
    void aLayeredDecorationShowsThroughAShortListAndTheBaseBackdropDoesNot() {
        Inventory inv = inv(27);
        MenuSpec spec = spec(
                """
                rows = 3
                items {
                  backdrop { slot = 1, material = GRAY_STAINED_GLASS_PANE, name = "back" }
                  frame { slot = 2, material = BLUE_STAINED_GLASS_PANE, name = "frame", priority = 5 }
                  grid { slots = [0, 1, 2], list { source = "warps:all", template { material = PAPER } } }
                }
                """);

        populate(inv, spec, Map.of("warps:all", List.of("spawn")));

        assertThat(materialAt(inv, 0)).isEqualTo(Material.PAPER);
        assertThat(materialAt(inv, 1))
                .as("the base backdrop is what the list replaces")
                .isNull();
        assertThat(materialAt(inv, 2))
                .as("a frame the author layered above the backdrop survives the gap")
                .isEqualTo(Material.BLUE_STAINED_GLASS_PANE);
    }

    // -- the content regions ----------------------------------------------------------------------------------

    @Test
    void aRegionWinsItsSlotsOverTheChromeUnderneath() {
        Inventory inv = inv(27);
        contents.register("deposit", new FixedProvider(List.of(new ItemStack(Material.EMERALD)), true));
        MenuSpec spec = spec(
                """
                rows = 3
                items { filler { slots = [0, 1], material = GRAY_STAINED_GLASS_PANE, name = "pane" } }
                content { deposit { slots = [1] } }
                """);

        populate(inv, spec, Map.of());

        assertThat(materialAt(inv, 0)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(materialAt(inv, 1))
                .as("a region is painted last so it always wins the slots it declares")
                .isEqualTo(Material.EMERALD);
    }

    /** An unregistered region leaves a hole, not a stale tile a viewer could pick up and walk away with. */
    @Test
    void aRegionWithNoProviderClearsItsSlotsRatherThanLeavingTheChrome() {
        Inventory inv = inv(27);
        MenuSpec spec = spec(
                """
                rows = 3
                items { filler { slots = [0, 1], material = GRAY_STAINED_GLASS_PANE, name = "pane" } }
                content { nobody-wired-this { slots = [1] } }
                """);

        populate(inv, spec, Map.of());

        assertThat(materialAt(inv, 0)).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(materialAt(inv, 1)).isNull();
    }

    @Test
    void aRegionIsNotRoutedThroughTheSpecsClickMap() {
        Inventory inv = inv(27);
        contents.register("deposit", new FixedProvider(List.of(new ItemStack(Material.EMERALD)), true));

        populate(inv, spec("rows = 3\ncontent { deposit { slots = [1] } }"), Map.of());

        assertThat(routed)
                .as("a click in a region is resolved against the region, never against an item")
                .isEmpty();
    }

    // -- redraw -----------------------------------------------------------------------------------------------

    /**
     * On a redraw the slots of a viewer-filled region hold stacks the feature has not read back yet. The chrome
     * underneath is redrawn on every pass and would paint over them, so they are taken aside and put back.
     */
    @Test
    void aRedrawKeepsWhatTheViewerPutInARegionThatDoesNotRepaint() {
        Inventory inv = inv(27);
        contents.register("deposit", new FixedProvider(List.of(), false));
        MenuSpec spec = spec(
                """
                rows = 3
                items { filler { slots = [0, 1], material = GRAY_STAINED_GLASS_PANE, name = "pane" } }
                content { deposit { slots = [1] } }
                """);
        inv.setItem(1, new ItemStack(Material.DIAMOND_SWORD));

        renderer.populate(inv, spec, ctx(), routed::put, Map.of(), false);

        assertThat(materialAt(inv, 1)).isEqualTo(Material.DIAMOND_SWORD);
    }

    /** A slot the viewer has just emptied stays empty, or the filler underneath is read back as a deposit. */
    @Test
    void aRedrawKeepsASlotTheViewerEmptiedEmpty() {
        Inventory inv = inv(27);
        contents.register("deposit", new FixedProvider(List.of(), false));
        MenuSpec spec = spec(
                """
                rows = 3
                items { filler { slots = [0, 1], material = GRAY_STAINED_GLASS_PANE, name = "pane" } }
                content { deposit { slots = [1] } }
                """);

        renderer.populate(inv, spec, ctx(), routed::put, Map.of(), false);

        assertThat(materialAt(inv, 1)).isNull();
    }

    @Test
    void aFirstPaintFillsAViewerRegionRatherThanHoldingWhateverIsThere() {
        Inventory inv = inv(27);
        FixedProvider provider = new FixedProvider(Arrays.asList(null, new ItemStack(Material.EMERALD)), false);
        contents.register("deposit", provider);
        MenuSpec spec = spec("rows = 3\ncontent { deposit { slots = [0, 1] } }");
        inv.setItem(1, new ItemStack(Material.BEDROCK));

        renderer.populate(inv, spec, ctx(), routed::put, Map.of(), true);

        assertThat(provider.renders).isEqualTo(1);
        assertThat(materialAt(inv, 1))
                .as("the window is new on a first paint, so there is nothing of the viewer's to protect")
                .isEqualTo(Material.EMERALD);
    }

    @Test
    void aRedrawStillRepaintsARegionThatSaysItRepaints() {
        Inventory inv = inv(27);
        FixedProvider provider = new FixedProvider(List.of(new ItemStack(Material.EMERALD)), true);
        contents.register("live", provider);
        MenuSpec spec = spec("rows = 3\ncontent { live { slots = [0] } }");

        renderer.populate(inv, spec, ctx(), routed::put, Map.of(), false);

        assertThat(provider.renders).isEqualTo(1);
        assertThat(materialAt(inv, 0)).isEqualTo(Material.EMERALD);
    }

    // -- the bottom inventory ---------------------------------------------------------------------------------

    @Test
    void aBottomItemIsPaintedIntoThePlayerInventoryAndRoutedUnderItsRawSlot() {
        MenuSpec spec = spec(
                """
                bottom-inventory = true
                items {
                  top { slot = 0, material = DIAMOND, name = "top" }
                  bottom { slot = 54, material = STONE, name = "bottom" }
                }
                """);

        renderer.populateBottom(viewer.getInventory(), spec, ctx(), routed::put, Map.of());

        assertThat(routed.keySet())
                .as("the raw slot is what a later click carries, so that is what the click map is keyed by")
                .containsExactly(54);
        assertThat(viewer.getInventory().getItem(9))
                .as("the first bottom raw slot is the first main-storage slot, because the hotbar is laid out last")
                .isNotNull();
        assertThat(Objects.requireNonNull(viewer.getInventory().getItem(9)).getType())
                .isEqualTo(Material.STONE);
        assertThat(viewer.getInventory().getItem(0))
                .as("player slot 0 is the hotbar, which raw 81 addresses rather than raw 54")
                .isNull();
    }

    @Test
    void aTopHalfItemNeverReachesThePlayerInventory() {
        MenuSpec spec = spec("bottom-inventory = true\nitems { top { slot = 0, material = DIAMOND, name = \"top\" } }");

        renderer.populateBottom(viewer.getInventory(), spec, ctx(), routed::put, Map.of());

        assertThat(routed).isEmpty();
        assertThat(viewer.getInventory().getItem(9)).isNull();
    }

    @Test
    void theBottomCanvasIsClearedSoARerenderLeavesNoStaleTile() {
        viewer.getInventory().setItem(0, new ItemStack(Material.BEDROCK));
        MenuSpec spec = spec("bottom-inventory = true\nitems { top { slot = 0, material = DIAMOND, name = \"top\" } }");

        renderer.populateBottom(viewer.getInventory(), spec, ctx(), routed::put, Map.of());

        assertThat(viewer.getInventory().getItem(0))
                .as("the viewer's real items live in the holder snapshot, never in this canvas")
                .isNull();
    }

    // -- the flat-text delegations ----------------------------------------------------------------------------

    @Test
    void theTitleIsResolvedThroughTheSamePathAnItemNameTakes() {
        MenuSpec spec = spec("rows = 1\ntitle = \"@menu.warps.title\"\nitems {}");

        assertThat(renderer.titleText(spec, ctx())).isEqualTo("menu.warps.title");
    }

    @Test
    void aFormReadsTheSameMaterialSpecAChestIconRendersFrom() {
        MenuSpec spec = spec("rows = 1\nitems { one { slot = 0, material = \"skull:Notch\", name = \"n\" } }");
        MenuItemSpec item = Objects.requireNonNull(spec.items().get("one"));

        assertThat(renderer.materialSpec(item, ctx())).isEqualTo("skull:Notch");
    }

    // -- the guards -------------------------------------------------------------------------------------------

    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    @Test
    void aRendererWithNoItemRendererIsRefusedAtWiringTimeRatherThanAtFirstPaint() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MenuRenderer(null, conditions))
                .withMessage("itemRenderer");
    }

    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    @Test
    void populateRefusesAMissingCollaboratorRatherThanPaintingHalfAWindow() {
        Inventory inv = inv(27);
        MenuSpec spec = spec("rows = 3\nitems {}");
        List<String> messages = new ArrayList<>();

        assertThatNullPointerException()
                .isThrownBy(() -> renderer.populate(null, spec, ctx(), routed::put, Map.of()))
                .satisfies(thrown -> messages.add(String.valueOf(thrown.getMessage())));
        assertThatNullPointerException()
                .isThrownBy(() -> renderer.populate(inv, spec, ctx(), routed::put, null))
                .satisfies(thrown -> messages.add(String.valueOf(thrown.getMessage())));

        assertThat(messages).containsExactly("inv", "resolvedLists");
    }
}

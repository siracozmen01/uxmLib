package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ContentProviderRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.providers.ContentClick;
import com.uxplima.uxmlib.menu.providers.ContentProvider;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.ContentRegionSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A content region is the one place an item may move inside a menu window, so it is the one place the router can
 * mint or eat one. Everything here is about who is asked, and what the answer is allowed to change.
 *
 * <p>The blanket cancel is the default the whole engine rests on: a test that only asserts "the click was cancelled"
 * would pass on every path, including the ones that were supposed to lift it. So each refusal below is paired with
 * the allowing case that proves the lift was reachable at all.
 */
class MenuListenerContentTest {

    /** A catalogue that hands every key straight back, so nothing here depends on a message file. */
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

    /** Answers one fixed verdict and records every click it was asked about. */
    private static final class Recording implements ContentProvider {

        private final boolean verdict;

        private final List<ContentClick> asked = new ArrayList<>();

        Recording(boolean verdict) {
            this.verdict = verdict;
        }

        @Override
        public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
            return List.of();
        }

        @Override
        public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
            asked.add(click);
            return verdict;
        }
    }

    /** Refuses every movement onto one named slot and allows the rest, so per-slot granularity is observable. */
    private static final class Reserving implements ContentProvider {

        private final int reserved;

        Reserving(int reserved) {
            this.reserved = reserved;
        }

        @Override
        public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
            return List.of();
        }

        @Override
        public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
            return click.slot() != reserved;
        }
    }

    private static final String SPEC = "rows = 3\ncontent { deposit { slots = [1, 2], editable = true } }";

    private ContentProviderRegistry contents;

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        contents = new ContentProviderRegistry();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()),
                new ConditionRegistry(),
                contents);
        SameThreadScheduler scheduler = new SameThreadScheduler();
        menus = new Menus(renderer, scheduler, new ListSourceRegistry());
        listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                () -> 1_000_000L,
                new PagedListSourceRegistry(),
                null,
                contents);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void open(String hocon) {
        menus.registerSpec("menu", new MenuSpecLoader().parse(hocon));
        menus.open(viewer, "menu", null);
    }

    private Inventory top() {
        return viewer.getOpenInventory().getTopInventory();
    }

    private InventoryClickEvent click(int rawSlot, ClickType type, InventoryAction action) {
        InventoryView view = viewer.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, type, action);
        listener.onClick(event);
        return event;
    }

    private InventoryClickEvent leftClick(int rawSlot) {
        return click(rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private static ItemStack diamond() {
        return new ItemStack(Material.DIAMOND);
    }

    /**
     * Put {@code stack} in the viewer's own inventory and hand back the raw slot the open view maps it to. The
     * mapping from a player's inventory index to a raw view slot is the server's business and is not worth
     * hardcoding: a raw slot that holds nothing makes the router return before it decides anything, and every test
     * here that asserts nothing moved would pass on the strength of that alone. So the slot is found, and not
     * finding one fails loudly rather than quietly weakening four tests.
     */
    private int holdInOwnRows(ItemStack stack) {
        viewer.getInventory().setItem(9, stack);
        InventoryView view = viewer.getOpenInventory();
        for (int raw = view.getTopInventory().getSize(); raw < view.countSlots(); raw++) {
            if (stack.equals(view.getItem(raw))) {
                return raw;
            }
        }
        throw new IllegalStateException("the open view maps no raw slot to the stack just placed");
    }

    // -- who is asked -----------------------------------------------------------------------------------------

    @Test
    void aRegionThatIsNotEditableIsNeverEvenPutToItsProvider() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open("rows = 3\ncontent { deposit { slots = [1, 2] } }");

        assertThat(leftClick(1).isCancelled()).isTrue();
        assertThat(provider.asked).isEmpty();
    }

    @Test
    void anEditableRegionWithNoRegisteredProviderStaysShut() {
        open(SPEC);
        assertThat(leftClick(1).isCancelled()).isTrue();
    }

    @Test
    void aProviderThatAllowsTheMovementLiftsTheCancel() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(leftClick(1).isCancelled()).isFalse();
        assertThat(provider.asked).hasSize(1);
    }

    @Test
    void aProviderThatRefusesTheMovementLeavesItCancelled() {
        Recording provider = new Recording(false);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(leftClick(1).isCancelled()).isTrue();
        assertThat(provider.asked).hasSize(1);
    }

    @Test
    void aSlotOutsideEveryRegionIsNotTheProvidersBusiness() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(leftClick(5).isCancelled()).isTrue();
        assertThat(provider.asked).isEmpty();
    }

    // -- the gestures no provider is allowed to permit ----------------------------------------------------------

    /**
     * A double-click gathers matching stacks from the whole window, and the chrome tiles are not real items, so
     * letting one through would mint them. The provider is not asked, because there is no one slot to ask about.
     */
    @Test
    void aDoubleClickIsRefusedEvenInARegionWhoseProviderAllowsEverything() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(click(1, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR)
                        .isCancelled())
                .isTrue();
        assertThat(provider.asked).isEmpty();
    }

    /** A hotbar swap pulls from a slot outside the region, which is not the movement the provider was asked about. */
    @Test
    void aHotbarSwapIsRefusedEvenInARegionWhoseProviderAllowsEverything() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(click(1, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP).isCancelled())
                .isTrue();
        assertThat(provider.asked).isEmpty();
    }

    @Test
    void anOffHandSwapIsRefusedTheSameWay() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        assertThat(click(1, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP).isCancelled())
                .isTrue();
        assertThat(provider.asked).isEmpty();
    }

    // -- what the provider is told the viewer is doing ----------------------------------------------------------

    /**
     * The three kinds are decided from the cursor and the slot together, so a provider can allow taking out while
     * refusing putting in. Asserted as one test because each kind alone would pass an implementation that always
     * reported it.
     */
    @Test
    void theKindIsReadFromTheCursorAndTheSlotTogether() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        top().setItem(1, diamond());
        viewer.getOpenInventory().setCursor(null);
        leftClick(1);
        assertThat(provider.asked.get(0).kind()).isEqualTo(ContentClick.Kind.TAKE);

        top().setItem(1, null);
        viewer.getOpenInventory().setCursor(diamond());
        leftClick(1);
        assertThat(provider.asked.get(1).kind()).isEqualTo(ContentClick.Kind.INSERT);

        top().setItem(1, diamond());
        viewer.getOpenInventory().setCursor(diamond());
        leftClick(1);
        assertThat(provider.asked.get(2).kind()).isEqualTo(ContentClick.Kind.SWAP);
    }

    /** A shift-click takes out whatever the cursor holds, so it is a TAKE rather than a SWAP. */
    @Test
    void aShiftClickOnAFilledSlotIsATakeWhateverTheCursorHolds() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        top().setItem(1, diamond());
        viewer.getOpenInventory().setCursor(diamond());

        click(1, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(provider.asked.get(0).kind()).isEqualTo(ContentClick.Kind.TAKE);
    }

    @Test
    void theClickCarriesTheSlotAndItsPositionWithinTheRegion() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);

        leftClick(2);

        assertThat(provider.asked.get(0).slot()).isEqualTo(2);
        assertThat(provider.asked.get(0).index()).isEqualTo(1);
    }

    // -- the shift-click the engine performs itself -------------------------------------------------------------

    /**
     * Vanilla would scatter a shift-clicked stack across whatever top slots are free, chrome gaps included, so the
     * engine performs the insert itself: into the region's first free slot, with the source cleared and the event
     * left cancelled so vanilla does not also move it.
     */
    @Test
    void aShiftClickFromTheViewersOwnRowsLandsInTheRegionsFirstFreeSlot() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        top().setItem(1, diamond());
        int raw = holdInOwnRows(new ItemStack(Material.EMERALD));

        InventoryClickEvent event = click(raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(event.isCancelled()).isTrue();
        assertThat(top().getItem(2)).isEqualTo(new ItemStack(Material.EMERALD));
        assertThat(event.getCurrentItem()).isNull();
    }

    @Test
    void aShiftClickTheProviderRefusesMovesNothing() {
        Recording provider = new Recording(false);
        contents.register("deposit", provider);
        open(SPEC);
        click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(top().getItem(1)).isNull();
        assertThat(top().getItem(2)).isNull();
    }

    @Test
    void aShiftClickIntoAFullRegionMovesNothing() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        top().setItem(1, diamond());
        top().setItem(2, diamond());
        click(
                holdInOwnRows(new ItemStack(Material.EMERALD)),
                ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(top().getItem(1)).isEqualTo(diamond());
        assertThat(top().getItem(2)).isEqualTo(diamond());
    }

    /**
     * A provider is asked per movement everywhere else in the router, and the shift path is no exception: refusing
     * the region's first free slot is a statement about that slot, not about the region. A provider that reserves its
     * first slot and takes the rest is saying something the contract lets it say, so the stack goes to the next slot
     * that accepts it rather than nowhere.
     */
    @Test
    void aRefusalOnOneSlotSkipsThatSlotRatherThanTheWholeRegion() {
        contents.register("deposit", new Reserving(1));
        open(SPEC);
        int raw = holdInOwnRows(new ItemStack(Material.EMERALD));

        click(raw, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);

        assertThat(top().getItem(1)).isNull();
        assertThat(top().getItem(2)).isEqualTo(new ItemStack(Material.EMERALD));
    }

    /** A plain click in the viewer's own rows is not an insert: only a shift-click asks the engine to move a stack. */
    @Test
    void aPlainClickInTheViewersOwnRowsMovesNothing() {
        Recording provider = new Recording(true);
        contents.register("deposit", provider);
        open(SPEC);
        leftClick(holdInOwnRows(new ItemStack(Material.EMERALD)));

        assertThat(top().getItem(1)).isNull();
        assertThat(provider.asked).isEmpty();
    }
}

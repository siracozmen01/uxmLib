package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.api.event.MenuClickEvent;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The click router. Every gesture inside a menu window arrives here, and almost every one of them ends the same way:
 * cancelled, so nothing an operator painted can be picked up. What differs is what runs afterwards, and which slot
 * the router believes was clicked.
 *
 * <p>The events are handed to the listener directly rather than posted through the plugin manager. The routing is
 * what is under test, and posting would add a registration the router does not decide.
 */
class MenuListenerTest {

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

    /** Cancels every menu click, so the veto path can be driven. */
    static final class ClickVetoer implements Listener {

        private final List<Integer> seen = new ArrayList<>();

        @EventHandler
        public void onClick(MenuClickEvent event) {
            seen.add(event.getSlot());
            event.setCancelled(true);
        }
    }

    /** Records the menu click events that reach it without vetoing any. */
    static final class ClickWatcher implements Listener {

        private final List<MenuClickEvent> seen = new ArrayList<>();

        @EventHandler
        public void onClick(MenuClickEvent event) {
            seen.add(event);
        }
    }

    private final List<String> fired = new ArrayList<>();

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private ActionRegistry actions;

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        actions = new ActionRegistry();
        for (String id : List.of("one", "two", "three")) {
            actions.register(id, ctx -> fired.add(id));
        }
        SameThreadScheduler scheduler = new SameThreadScheduler();
        menus = new Menus(renderer(), scheduler, new ListSourceRegistry());
        listener = listenerWithCooldown(0L);
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    private MenuListener listenerWithCooldown(long cooldownMs) {
        return new MenuListener(
                renderer(),
                actions,
                new ConditionRegistry(),
                new SameThreadScheduler(),
                plugin,
                null,
                null,
                null,
                cooldownMs,
                now::get,
                new PagedListSourceRegistry(),
                null,
                null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Register {@code hocon} under {@code id} and open it, the shape every routing test starts from. */
    private void open(String hocon) {
        menus.registerSpec("menu", new MenuSpecLoader().parse(hocon));
        menus.open(viewer, "menu", null);
    }

    private InventoryClickEvent click(int rawSlot, ClickType type) {
        InventoryView view = viewer.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, type, InventoryAction.PICKUP_ALL);
        listener.onClick(event);
        return event;
    }

    private InventoryClickEvent leftClick(int rawSlot) {
        return click(rawSlot, ClickType.LEFT);
    }

    // -- what the router refuses to let move ------------------------------------------------------------------

    /**
     * A window the engine did not open carries no holder, so the router has nothing to say about it. Cancelling here
     * would break every other plugin's inventory on the server.
     */
    @Test
    void aClickInAWindowTheEngineDidNotOpenIsLeftAlone() {
        viewer.openInventory(Bukkit.createInventory(null, 27));
        assertThat(leftClick(0).isCancelled()).isFalse();
    }

    @Test
    void aClickOnAMenuSlotIsCancelledEvenWhenNothingIsDrawnThere() {
        open("rows = 3");
        assertThat(leftClick(4).isCancelled()).isTrue();
        assertThat(fired).isEmpty();
    }

    /**
     * The viewer's own rows are part of the same window. Shift-clicking there would otherwise throw the stack into
     * the menu, so the cancel covers the whole view rather than the top inventory.
     */
    @Test
    void aClickInTheViewersOwnRowsIsCancelledToo() {
        open("rows = 3");
        assertThat(click(30, ClickType.SHIFT_LEFT).isCancelled()).isTrue();
    }

    // -- which list a gesture runs ----------------------------------------------------------------------------

    @Test
    void aLeftClickRunsTheLeftListAndNothingElse() {
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"], right = [\"two\"] } } }");
        leftClick(4);
        assertThat(fired).containsExactly("one");
    }

    @Test
    void aRightClickRunsTheRightList() {
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"], right = [\"two\"] } } }");
        click(4, ClickType.RIGHT);
        assertThat(fired).containsExactly("two");
    }

    @Test
    void aGestureTheItemDeclaresNoListForRunsNothing() {
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"] } } }");
        click(4, ClickType.MIDDLE);
        assertThat(fired).isEmpty();
    }

    @Test
    void theActionsOfOneGestureRunInTheOrderTheFileWroteThem() {
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"three\", \"one\", \"two\"] } } }");
        leftClick(4);
        assertThat(fired).containsExactly("three", "one", "two");
    }

    @Test
    void aClickOnASlotTheMenuNeverDrewRunsNothing() {
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"] } } }");
        leftClick(5);
        assertThat(fired).isEmpty();
    }

    // -- the event a host can veto ----------------------------------------------------------------------------

    @Test
    void theClickEventNamesTheOperatorsOwnKeyForTheItem() {
        ClickWatcher watcher = new ClickWatcher();
        Bukkit.getPluginManager().registerEvents(watcher, plugin);
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"] } } }");
        leftClick(4);
        assertThat(watcher.seen).hasSize(1);
        assertThat(watcher.seen.get(0).getItemId()).isEqualTo("go");
        assertThat(watcher.seen.get(0).getSlot()).isEqualTo(4);
        assertThat(watcher.seen.get(0).getMenuId()).isEqualTo("menu");
    }

    @Test
    void aVetoedClickRunsNoActionsButIsStillCancelled() {
        ClickVetoer vetoer = new ClickVetoer();
        Bukkit.getPluginManager().registerEvents(vetoer, plugin);
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"] } } }");
        assertThat(leftClick(4).isCancelled()).isTrue();
        assertThat(vetoer.seen).containsExactly(4);
        assertThat(fired).isEmpty();
    }

    /** An empty slot fires no event at all: there is no item for a host to recognise. */
    @Test
    void anEmptySlotFiresNoClickEvent() {
        ClickWatcher watcher = new ClickWatcher();
        Bukkit.getPluginManager().registerEvents(watcher, plugin);
        open("rows = 3");
        leftClick(4);
        assertThat(watcher.seen).isEmpty();
    }

    // -- the anti-spam window ---------------------------------------------------------------------------------

    /**
     * The cooldown is a per-menu window: the first click of a menu passes, a second inside the window is swallowed,
     * and one after it passes again. The three are asserted together because each alone would pass a throttle that
     * always refused, always allowed, or never re-armed.
     */
    @Test
    void aSecondClickInsideTheWindowIsSwallowedAndOneAfterItIsNot() {
        listener = listenerWithCooldown(250L);
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"] } } }");

        leftClick(4);
        assertThat(fired)
                .as("the first click of a menu is never inside a window")
                .containsExactly("one");

        now.addAndGet(100L);
        leftClick(4);
        assertThat(fired).as("100ms into a 250ms window").containsExactly("one");

        now.addAndGet(200L);
        leftClick(4);
        assertThat(fired).containsExactly("one", "one");
    }

    /**
     * The opening click of a menu has no previous click to be too close to. The clock is a supplier the host gives
     * the engine, and a monotonic one (the natural choice for a cooldown) starts near its own origin rather than near
     * an epoch: spelling "never clicked" as a reading of zero would let such a clock imitate it, and the first click
     * of every menu would fall inside a window that never opened. This drives the clock from zero, which is where a
     * host that reached for a monotonic source would start.
     */
    @Test
    void theFirstClickPassesUnderAClockThatStartsInsideTheWindow() {
        now.set(0L);
        listener = listenerWithCooldown(250L);
        open("rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = [\"one\"] } } }");

        leftClick(4);

        assertThat(fired).containsExactly("one");
    }

    // -- drags ------------------------------------------------------------------------------------------------

    @Test
    void aDragOverAMenuWindowIsCancelled() {
        open("rows = 3");
        InventoryView view = viewer.getOpenInventory();
        InventoryDragEvent event = new InventoryDragEvent(
                view,
                new ItemStack(Material.DIAMOND),
                new ItemStack(Material.DIAMOND),
                false,
                Map.of(4, new ItemStack(Material.DIAMOND)));
        listener.onDrag(event);
        assertThat(event.isCancelled()).isTrue();
    }

    // -- installation -----------------------------------------------------------------------------------------

    @Test
    void installingRegistersTheHandlersAndUninstallingRemovesThem() {
        listener.install();
        assertThat(InventoryClickEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(registered -> registered.getListener() == listener);
        listener.uninstall();
        assertThat(InventoryClickEvent.getHandlerList().getRegisteredListeners())
                .noneMatch(registered -> registered.getListener() == listener);
    }
}

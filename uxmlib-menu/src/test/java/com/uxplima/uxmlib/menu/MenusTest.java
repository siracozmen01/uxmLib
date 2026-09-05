package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.api.event.MenuOpenEvent;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.LastMenu;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.spec.Ref;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The facade a feature holds: what it registers, what an open leaves on the viewer's screen, and what the three
 * read-only lookups answer while that window is up. The engine keeps no player-keyed map, so every one of those
 * answers is recovered from the window's own holder: the tests here go through the facade rather than the holder, so
 * they pin what a caller can actually observe.
 *
 * <p>The scheduler runs inline, so an open that hops off the tick thread and back has finished by the time the call
 * returns. That is the point of the double rather than a convenience: it also counts the hops, so the order an open
 * takes them in is itself assertable.
 */
class MenusTest {

    /** A catalogue that hands every key straight back, so a rendered title is readable in an assertion. */
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

    /** Cancels every open, so the veto path can be driven without a plugin of its own. */
    static final class Vetoer implements Listener {

        private final List<String> seen = new ArrayList<>();

        @EventHandler(priority = EventPriority.NORMAL)
        public void onOpen(MenuOpenEvent event) {
            seen.add(event.getMenuId());
            event.setCancelled(true);
        }
    }

    /**
     * Holds the entity hop rather than running it, so a test can act in the gap an open leaves between resolving its
     * lists off the tick thread and building the window on the viewer's own. The async hop still runs inline: it is
     * the second half of the round trip that this needs to be able to interrupt.
     */
    static final class DeferringScheduler extends SameThreadScheduler {

        private final List<Runnable> held = new ArrayList<>();

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            entityHops++;
            held.add(task);
            return FINISHED;
        }

        /** Run everything held so far, in the order it was scheduled. */
        void runHeldHop() {
            List<Runnable> due = List.copyOf(held);
            held.clear();
            due.forEach(Runnable::run);
        }
    }

    private SameThreadScheduler scheduler;

    private ListSourceRegistry lists;

    private Menus menus;

    private Player viewer;

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new SameThreadScheduler();
        lists = new ListSourceRegistry();
        menus = new Menus(renderer(), scheduler, lists);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    private static MenuSpec spec(String hocon) {
        return new MenuSpecLoader().parse(hocon);
    }

    /** Register {@code hocon} under {@code id} and hand back the parsed spec, the shape most tests here start from. */
    private MenuSpec register(String id, String hocon) {
        MenuSpec spec = spec(hocon);
        menus.registerSpec(id, spec);
        return spec;
    }

    private Inventory top() {
        return viewer.getOpenInventory().getTopInventory();
    }

    // -- the spec registry ------------------------------------------------------------------------------------

    @Test
    void aSpecIsFoundUnderTheIdItWasRegisteredWith() {
        MenuSpec spec = register("shop", "rows = 3");
        assertThat(menus.registeredSpec("shop")).hasValue(spec);
    }

    @Test
    void anIdNothingWasRegisteredUnderAnswersEmptyRatherThanFailing() {
        assertThat(menus.registeredSpec("nothing")).isEmpty();
    }

    @Test
    void registeringTheSameIdTwiceKeepsTheSecondSpec() {
        register("shop", "rows = 3");
        MenuSpec second = register("shop", "rows = 5");
        assertThat(menus.registeredSpec("shop")).hasValue(second);
    }

    @Test
    void unregisteringDropsTheSpecAndUnregisteringAgainIsNotAFailure() {
        register("shop", "rows = 3");
        menus.unregisterSpec("shop");
        menus.unregisterSpec("shop");
        assertThat(menus.registeredSpec("shop")).isEmpty();
    }

    @Test
    void theRegistryRefusesANullIdOrSpec() {
        assertThatNullPointerException().isThrownBy(() -> menus.registerSpec(null, spec("rows = 3")));
        assertThatNullPointerException().isThrownBy(() -> menus.registerSpec("shop", null));
        assertThatNullPointerException().isThrownBy(() -> menus.unregisterSpec(null));
        assertThatNullPointerException().isThrownBy(() -> menus.registeredSpec(null));
    }

    // -- what an open leaves on the screen --------------------------------------------------------------------

    @Test
    void anUnknownSpecIdFailsLoudlyBeforeTheOpenTakesASingleHop() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> menus.open(viewer, "missing", null))
                .withMessageContaining("missing");
        assertThat(scheduler.asyncHops).isZero();
        assertThat(scheduler.entityHops).isZero();
    }

    @Test
    void theOpenResolvesListsOffTheTickThreadBeforeItTouchesTheWindow() {
        register("shop", "rows = 3");
        menus.open(viewer, "shop", null);
        assertThat(scheduler.asyncHops).isOne();
        assertThat(scheduler.entityHops).isOne();
    }

    @Test
    void theWindowTheViewerEndsUpLookingAtIsTheRegisteredMenu() {
        register("shop", "rows = 3");
        menus.open(viewer, "shop", null);
        assertThat(menus.menuIdOf(top())).hasValue("shop");
    }

    @Test
    void aChestMenuIsAsManySlotsAsItsSpecHasRows() {
        register("shop", "rows = 4");
        menus.open(viewer, "shop", null);
        assertThat(top().getSize()).isEqualTo(36);
    }

    @Test
    void anInventoryThatIsNotAnEngineWindowIsNotClaimedAsOne() {
        assertThat(menus.menuIdOf(Bukkit.createInventory(null, 27))).isEmpty();
    }

    @Test
    void aNegativePageOpensOnTheFirstPageRatherThanStepping() {
        register("shop", "rows = 3");
        menus.open(viewer, "shop", null, -5);
        assertThat(menus.currentMenu(viewer.getUniqueId()))
                .get()
                .extracting(OpenMenuInfo::page)
                .isEqualTo(1);
    }

    @Test
    void theCurrentMenuReadsBackTheIdPageAndShapeOfTheOpenWindow() {
        register("shop", "rows = 4");
        menus.open(viewer, "shop", null, 2, Map.of("amount", "10"));
        assertThat(menus.currentMenu(viewer.getUniqueId()))
                .hasValue(new OpenMenuInfo("shop", 3, 4, Map.of("amount", "10")));
    }

    @Test
    void aPlayerWhoIsNotOnlineIsInNoMenu() {
        assertThat(menus.currentMenu(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aPlayerLookingAtSomethingThatIsNotAnEngineMenuIsInNoMenu() {
        viewer.openInventory(Bukkit.createInventory(null, 27));
        assertThat(menus.currentMenu(viewer.getUniqueId())).isEmpty();
    }

    /**
     * The arguments the window reads back are not the caller's map. This pins the outcome rather than any one copy:
     * the context, the history entry and the read-back value each copy at their own door, so this test passes with
     * the facade's own copy deleted. The test below is the one that pins that copy.
     */
    @Test
    void theArgumentsAreCopiedSoALaterEditByTheCallerDoesNotReachTheOpenWindow() {
        register("shop", "rows = 3");
        Map<String, String> arguments = new HashMap<>(Map.of("amount", "10"));
        menus.open(viewer, "shop", null, 0, arguments);
        arguments.put("amount", "99");
        arguments.put("added", "later");
        assertThat(menus.currentMenu(viewer.getUniqueId()))
                .get()
                .extracting(OpenMenuInfo::arguments)
                .isEqualTo(Map.of("amount", "10"));
    }

    /**
     * An open crosses two thread hops: the arguments are copied on the calling thread, resolved against the list
     * sources off the tick thread, and read a second time on the viewer's entity thread when the window is built. The
     * caller owns their map throughout, and on a real server has the whole of that round trip to write to it. The
     * facade's copy at the door is what closes that window, and only a scheduler that holds the entity hop can see
     * it: with an inline one the caller never gets a turn, and the copy looks redundant because the values it hands
     * the map to copy again at their own doors.
     */
    @Test
    void anEditMadeWhileTheOpenIsStillInFlightDoesNotReachTheWindow() {
        DeferringScheduler deferring = new DeferringScheduler();
        Menus engine = new Menus(renderer(), deferring, lists);
        engine.registerSpec("shop", spec("rows = 3"));
        Map<String, String> arguments = new HashMap<>(Map.of("amount", "10"));

        engine.open(viewer, "shop", null, 0, arguments);
        arguments.put("amount", "99");
        deferring.runHeldHop();

        assertThat(engine.currentMenu(viewer.getUniqueId()))
                .get()
                .extracting(OpenMenuInfo::arguments)
                .isEqualTo(Map.of("amount", "10"));
    }

    // -- the window a spec asks for ---------------------------------------------------------------------------

    @Test
    void aSpecNamingAnInventoryTypeOpensThatShapeRatherThanAChest() {
        register("tools", "rows = 3\ninventory-type = hopper");
        menus.open(viewer, "tools", null);
        assertThat(top().getType()).isEqualTo(InventoryType.HOPPER);
    }

    @Test
    void anInventoryTypeIsReadWithoutRegardToCaseOrSurroundingSpace() {
        register("tools", "rows = 3\ninventory-type = \"  HoPPeR \"");
        menus.open(viewer, "tools", null);
        assertThat(top().getType()).isEqualTo(InventoryType.HOPPER);
    }

    /**
     * The aliases exist so a spec author can write the block name they know. Each is checked against the type it
     * stands for rather than against "not a chest", which would pass whichever of the two the alias resolved to.
     */
    @Test
    void theBlockNameAliasesReachTheSameTypeTheirCanonicalNameDoes() {
        assertThat(typeOf("shulker")).isEqualTo(typeOf("shulker_box")).isEqualTo(InventoryType.SHULKER_BOX);
        assertThat(typeOf("ender")).isEqualTo(typeOf("ender_chest")).isEqualTo(InventoryType.ENDER_CHEST);
        assertThat(typeOf("workbench")).isEqualTo(typeOf("crafting")).isEqualTo(InventoryType.WORKBENCH);
        assertThat(typeOf("brewing")).isEqualTo(typeOf("brewing_stand")).isEqualTo(InventoryType.BREWING);
    }

    /**
     * A type nobody can spell is a soft miss: the viewer meets the default chest at the spec's own row count rather
     * than a failed open or a window of the wrong shape.
     */
    @Test
    void aTypeTheEngineDoesNotKnowFallsBackToTheChestTheRowsAskFor() {
        register("odd", "rows = 2\ninventory-type = teleporter");
        menus.open(viewer, "odd", null);
        assertThat(top().getSize()).isEqualTo(18);
        assertThat(menus.menuIdOf(top())).hasValue("odd");
    }

    @Test
    void namingTheChestExplicitlyIsTheSameAsNamingNothing() {
        register("plain", "rows = 2\ninventory-type = chest");
        menus.open(viewer, "plain", null);
        assertThat(top().getSize()).isEqualTo(18);
    }

    private InventoryType typeOf(String inventoryType) {
        register("shaped", "rows = 3\ninventory-type = " + inventoryType);
        menus.open(viewer, "shaped", null);
        return top().getType();
    }

    // -- the window a feature reads back --------------------------------------------------------------------

    @Test
    void theOpenWindowIsHandedBackOnlyToACallerThatNamesTheMenuItIs() {
        register("shop", "rows = 3");
        menus.open(viewer, "shop", null);
        assertThat(menus.openWindow(viewer, "shop")).hasValue(top());
        assertThat(menus.openWindow(viewer, "other")).isEmpty();
    }

    @Test
    void aViewerLookingAtNoEngineMenuHasNoWindowToRead() {
        assertThat(menus.openWindow(viewer, "shop")).isEmpty();
    }

    // -- the list sources an open resolves --------------------------------------------------------------------

    @Test
    void aRegisteredListSourceIsAskedOnceForTheOpenAndSeesTheViewer() {
        List<MenuContext> asked = new ArrayList<>();
        lists.register("warps", ctx -> {
            asked.add(ctx);
            return List.of("spawn", "shop");
        });
        register("warps", "rows = 3\nitems { row { slots = [0, 1, 2], material = PAPER, list { source = warps } } }");
        menus.open(viewer, "warps", null);
        assertThat(asked).hasSize(1);
        assertThat(asked.get(0).viewer()).isEqualTo(viewer);
    }

    /**
     * A source nobody registered is a wiring gap, not a failure: the open goes through with an empty list, so the
     * operator meets an empty grid they can see rather than a window that never appeared.
     */
    @Test
    void aListSourceNothingRegisteredStillOpensTheWindow() {
        register("warps", "rows = 3\nitems { row { slots = [0, 1, 2], material = PAPER, list { source = gone } } }");
        menus.open(viewer, "warps", null);
        assertThat(menus.menuIdOf(top())).hasValue("warps");
    }

    // -- the veto ---------------------------------------------------------------------------------------------

    /**
     * The event fires at the one choke-point every open funnels through, before the window is built at all. A
     * cancelled open must therefore leave the viewer looking at what they were looking at, not at an empty menu.
     */
    @Test
    void aCancelledOpenBuildsNoWindowAtAll() {
        Vetoer vetoer = new Vetoer();
        Bukkit.getPluginManager().registerEvents(vetoer, plugin);
        register("shop", "rows = 3");
        menus.open(viewer, "shop", null);
        assertThat(vetoer.seen).containsExactly("shop");
        assertThat(menus.currentMenu(viewer.getUniqueId())).isEmpty();
    }

    // -- the requirement gate -----------------------------------------------------------------------------------

    /**
     * An engine wired without a condition registry cannot test anything, and the choice made there is to open rather
     * than to shut: a fixture that never wired conditions is not a menu whose requirements all failed.
     */
    @Test
    void anEngineWithNoConditionRegistryPassesEvenARequirementItCannotRead() {
        assertThat(menus.passes(viewer, List.of(Ref.parse("has-money:100")), Map.of()))
                .isTrue();
    }

    @Test
    void theGateRefusesNullArgumentsRatherThanTreatingThemAsNone() {
        assertThatNullPointerException().isThrownBy(() -> menus.passes(viewer, List.of(), null));
        assertThatNullPointerException().isThrownBy(() -> menus.passes(viewer, null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> menus.passes(null, List.of(), Map.of()));
    }

    // -- the history an engine was wired without ------------------------------------------------------------

    @Test
    void anEngineWithNoHistoryRemembersNothingAndReopensNothing() {
        register("shop", "rows = 3");
        menus.open(viewer, "shop", null);
        assertThat(menus.lastMenuId(viewer.getUniqueId())).isEmpty();
        assertThat(menus.reopenLast(viewer)).isFalse();
    }

    /** With no history there is nothing beneath the open window, so a back step closes it rather than doing nothing. */
    @Test
    void aBackStepWithNoHistoryClosesTheWindow() {
        register("shop", "rows = 3");
        menus.open(viewer, "shop", null);
        menus.back(viewer);
        assertThat(menus.currentMenu(viewer.getUniqueId())).isEmpty();
    }

    // -- the history an engine was wired with ---------------------------------------------------------------

    private Menus withHistory(LastMenu history) {
        return new Menus(renderer(), scheduler, lists, null, null, null, history);
    }

    /**
     * Only a subject-less open is remembered. A feature menu carries a live domain object that must never be reopened
     * blind, so the two cases are asserted together: the pair is the rule, and either alone would pass a version that
     * recorded everything or nothing.
     */
    @Test
    void aSubjectLessOpenIsRememberedAndAFeatureMenuIsNot() {
        LastMenu history = new LastMenu();
        Menus engine = withHistory(history);
        engine.registerSpec("custom", spec("rows = 3"));
        engine.registerSpec("home", spec("rows = 3"));

        engine.open(viewer, "custom", null);
        assertThat(engine.lastMenuId(viewer.getUniqueId())).hasValue("custom");

        engine.open(viewer, "home", "a-home");
        assertThat(engine.lastMenuId(viewer.getUniqueId())).hasValue("custom");
    }

    @Test
    void reopeningTheLastMenuOpensItAgainWithoutStackingIt() {
        LastMenu history = new LastMenu();
        Menus engine = withHistory(history);
        engine.registerSpec("custom", spec("rows = 3"));
        engine.open(viewer, "custom", null);
        viewer.closeInventory();

        assertThat(engine.reopenLast(viewer)).isTrue();
        assertThat(engine.menuIdOf(top())).hasValue("custom");
        assertThat(engine.reopenLast(viewer)).isTrue();
        assertThat(history.back(viewer.getUniqueId())).isEmpty();
    }

    /**
     * A recorded menu can be dropped while it is still the viewer's reopen target. Reopening it blind would raise the
     * loud unknown-spec failure, so the stale entry answers false and the caller shows its own feedback.
     */
    @Test
    void reopeningAMenuThatHasSinceBeenUnregisteredAnswersFalseRatherThanFailing() {
        LastMenu history = new LastMenu();
        Menus engine = withHistory(history);
        engine.registerSpec("custom", spec("rows = 3"));
        engine.open(viewer, "custom", null);
        engine.unregisterSpec("custom");
        assertThat(engine.reopenLast(viewer)).isFalse();
    }
}

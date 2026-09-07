package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.spec.ClickKind;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.spec.Ref;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

/**
 * The three facade calls a plugin makes from outside a menu: run one action for a player, repaint the menu they are
 * looking at, and tear every menu down when the plugin stops. None of them is reached by a click, so none was covered
 * by the click tests, and each of them is the kind of call that is written once in a bootstrap and never looked at
 * again. The shutdown one carries the most: a refresh task that survives a disable re-renders a window belonging to
 * an engine that no longer exists.
 */
class MenusFacadeTest {

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

    /**
     * The global hop {@link SameThreadScheduler} refuses, taken here because shutdown is the one facade call that
     * needs it: the online roster is only coherent on the global region thread.
     */
    private static final class GlobalScheduler extends SameThreadScheduler {

        private int globalHops;

        @Override
        public com.uxplima.uxmlib.scheduler.TaskHandle global(Runnable task) {
            globalHops++;
            task.run();
            return FINISHED;
        }
    }

    /** A scheduler that only counts the global hop, for asserting that the sweep is handed over at all. */
    private static final class CountingScheduler extends SameThreadScheduler {

        private int globalHops;

        @Override
        public com.uxplima.uxmlib.scheduler.TaskHandle global(Runnable task) {
            globalHops++;
            task.run();
            return FINISHED;
        }
    }

    /**
     * A server that can answer the two thread questions the way Folia does on the thread it disables plugins
     * from: nothing is ticking, so it is not the global tick thread, but the server is stopping and this is one
     * of its tick threads, which is where Folia hands over ownership of every region.
     */
    // ServerMock overrides Server#getBanList without its type parameter, so any subclass inherits an unchecked
    // warning that -Werror turns into a build failure. It is MockBukkit's raw type, not ours.
    @SuppressWarnings("unchecked")
    private static final class PlatformServerMock extends org.mockbukkit.mockbukkit.ServerMock {

        private boolean globalTickThread = true;
        private boolean stopping;

        void foliaShutdownThread() {
            globalTickThread = false;
            stopping = true;
        }

        @Override
        public boolean isGlobalTickThread() {
            return globalTickThread;
        }

        @Override
        public boolean isStopping() {
            return stopping;
        }
    }

    private final List<MenuActionContext> ran = new ArrayList<>();

    private GlobalScheduler scheduler;

    private ActionRegistry actions;

    private Menus menus;

    private PlatformServerMock server;

    private Player viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new PlatformServerMock());
        MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new GlobalScheduler();
        actions = new ActionRegistry();
        actions.register("shop:buy", ran::add);
        menus = new Menus(renderer(), scheduler, new ListSourceRegistry(), null, actions, new ConditionRegistry());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    /** MockBukkit hands back a null top inventory once a window is closed, so every read of one goes through here. */
    private @Nullable Object holderOfOpenWindow() {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder();
    }

    private void open(String id, String hocon) {
        menus.registerSpec(id, new MenuSpecLoader().parse(hocon));
        menus.open(viewer, id, null);
    }

    // -- running one action from outside a menu -----------------------------------------------------------------

    @Test
    void aRegisteredActionRunsForTheNamedPlayer() {
        menus.execute(viewer, Ref.parse("shop:buy"));

        assertThat(ran).hasSize(1);
        assertThat(ran.get(0).viewer()).isSameAs(viewer);
    }

    @Test
    void anActionDrivenFromOutsideAMenuSeesALeftClick() {
        menus.execute(viewer, Ref.parse("shop:buy"));

        assertThat(ran.get(0).clickKind())
                .as("there is no gesture behind a facade call, so the engine has to name one and stay consistent")
                .isEqualTo(ClickKind.LEFT);
    }

    @Test
    void theArgumentsHandedInReachTheActionThroughItsContext() {
        menus.execute(viewer, Ref.parse("shop:buy"), Map.of("tier", "gold"));

        assertThat(ran.get(0).context().arguments())
                .as("a facade caller's map is the open's arguments, not the ref's own")
                .containsEntry("tier", "gold");
    }

    @Test
    void anArgumentTokenInsideTheRefIsSubstitutedFromThatMap() {
        actions.register("message", ran::add);

        menus.execute(viewer, Ref.parse("message:hello %argument_tier%"), Map.of("tier", "gold"));

        assertThat(ran.get(0).arg())
                .as("a ref written with an argument token is what the caller's map is for")
                .isEqualTo("hello gold");
    }

    @Test
    void aBareTokenIsLeftForThePlaceholderRegistryRatherThanReadFromTheArguments() {
        actions.register("message", ran::add);

        menus.execute(viewer, Ref.parse("message:hello %tier%"), Map.of("tier", "gold"));

        assertThat(ran.get(0).arg())
                .as("only the argument_ prefix reads the caller's map, so the two namespaces cannot collide")
                .isEqualTo("hello %tier%");
    }

    @Test
    void anArgumentTokenWithNothingBehindItBecomesEmptyRatherThanStayingLiteral() {
        actions.register("message", ran::add);

        menus.execute(viewer, Ref.parse("message:hello %argument_missing%"), Map.of("tier", "gold"));

        assertThat(ran.get(0).arg()).isEqualTo("hello ");
    }

    @Test
    void aRefWithNoTokenIsHandedOverUnchanged() {
        actions.register("message", ran::add);

        menus.execute(viewer, Ref.parse("message:plain"), Map.of("tier", "gold"));

        assertThat(ran.get(0).arg()).isEqualTo("plain");
    }

    @Test
    void anIdNobodyRegisteredRunsNothingRatherThanFailing() {
        assertThatCode(() -> menus.execute(viewer, Ref.parse("shop:nosuchaction")))
                .doesNotThrowAnyException();
        assertThat(ran).isEmpty();
    }

    @Test
    void anEngineBuiltWithNoActionRegistryRunsNothing() {
        Menus bare = new Menus(renderer(), scheduler, new ListSourceRegistry());

        assertThatCode(() -> bare.execute(viewer, Ref.parse("shop:buy"))).doesNotThrowAnyException();
        assertThat(ran).isEmpty();
    }

    // -- repainting the window a player is looking at ------------------------------------------------------------

    @Test
    void aRedrawKeepsTheSameWindowRatherThanOpeningASecond() {
        open("shop", "rows = 3\nitems { one { slot = 0, material = STONE, name = \"n\" } }");
        Inventory before = viewer.getOpenInventory().getTopInventory();

        menus.redraw(viewer, "shop");

        assertThat(viewer.getOpenInventory().getTopInventory())
                .as("a redraw that reopened would lose the viewer's place and flicker the window")
                .isSameAs(before);
    }

    @Test
    void aRedrawNamingAMenuTheViewerIsNotLookingAtDoesNothing() {
        open("shop", "rows = 3");
        Inventory before = viewer.getOpenInventory().getTopInventory();

        menus.redraw(viewer, "warps");

        assertThat(viewer.getOpenInventory().getTopInventory()).isSameAs(before);
    }

    @Test
    void aRedrawForAViewerInNoMenuIsNotAFailure() {
        assertThatCode(() -> menus.redraw(viewer, "shop")).doesNotThrowAnyException();
    }

    // -- tearing every menu down ---------------------------------------------------------------------------------

    @Test
    void shutdownClosesEveryWindowTheEngineOwns() {
        open("shop", "rows = 3");
        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);

        menus.shutdown();

        assertThat(holderOfOpenWindow() instanceof MenuHolder)
                .as("a window left open after the engine is gone routes its next click at nothing")
                .isFalse();
    }

    @Test
    void shutdownCancelsTheRefreshBeforeItClosesTheWindow() {
        open("shop", "rows = 3");
        MenuHolder holder =
                (MenuHolder) viewer.getOpenInventory().getTopInventory().getHolder();
        boolean[] cancelled = {false};
        holder.setRefreshHandle(new com.uxplima.uxmlib.scheduler.TaskHandle() {

            @Override
            public void cancel() {
                cancelled[0] = true;
            }

            @Override
            public boolean isCancelled() {
                return cancelled[0];
            }
        });

        menus.shutdown();

        assertThat(cancelled[0])
                .as("a timer that outlives the disable re-renders a window whose engine is gone")
                .isTrue();
    }

    @Test
    void shutdownRunsOnTheGlobalThreadBecauseThatIsWhereTheRosterIsCoherent() {
        menus.shutdown();

        assertThat(scheduler.globalHops).isEqualTo(1);
    }

    @Test
    void shutdownLeavesAWindowTheEngineDidNotOpenAlone() {
        Inventory other = Bukkit.createInventory(null, 27);
        viewer.openInventory(other);

        menus.shutdown();

        assertThat(viewer.getOpenInventory().getTopInventory())
                .as("closing another plugin's window on our disable is not ours to do")
                .isSameAs(other);
    }

    /**
     * The same sweep on Folia, on the thread Folia actually disables plugins from.
     *
     * <p>This is the test that decides whether a quiet log means anything. Folia halts every region tick and the
     * global tick before it disables a plugin, so {@code isGlobalTickThread} is false there and a fix that asked
     * only that question would refuse the sweep and leave every window open, while the log went quiet because the
     * exception was gone rather than because the work was done. It has to close the window, not merely not throw.
     */
    @Test
    void shutdownClosesTheWindowsOnFoliasShutdownThreadToo() {
        open("shop", "rows = 3");
        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);
        PluginMock plugin = MockBukkit.createMockPlugin("FoliaMenuTeardown");
        Menus disabling = new Menus(renderer(), new PaperScheduler(plugin), new ListSourceRegistry());
        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);
        server.foliaShutdownThread();

        disabling.shutdown();

        assertThat(holderOfOpenWindow() instanceof MenuHolder)
                .as("Folia disables plugins from the one thread it has handed every region to")
                .isFalse();
    }

    /**
     * The sweep is handed to the scheduler whether or not there is anything to close, which is what makes a quiet
     * shutdown log readable: a plugin cannot go silent by having no menu open. Without this the absence of a trace
     * would prove nothing about whether the teardown ran.
     */
    @Test
    void theSweepIsScheduledEvenWhenNoWindowIsOpen() {
        PluginMock plugin = MockBukkit.createMockPlugin("EmptyRosterTeardown");
        CountingScheduler counting = new CountingScheduler();
        Menus empty = new Menus(renderer(), counting, new ListSourceRegistry());
        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);

        empty.shutdown();

        assertThat(counting.globalHops)
                .as("a shutdown that schedules nothing is a shutdown nobody can tell apart from a skipped one")
                .isEqualTo(1);
    }

    /**
     * The uxmEssentials shutdown trace, at the level it was seen. The sweep is handed to the global region, and
     * once the plugin is disabled the server refuses to take it: the disable threw and every window stayed open
     * with no engine behind it. A second engine over a real {@link PaperScheduler} is what makes the point, since
     * the sweep reads the online roster rather than one engine's own bookkeeping and the fixture engine is the
     * one that can open a window without a live server thread.
     */
    @Test
    void shutdownStillClosesTheWindowsOnceTheOwningPluginIsDisabled() {
        open("shop", "rows = 3");
        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);
        PluginMock plugin = MockBukkit.createMockPlugin("MenuTeardown");
        Menus disabling = new Menus(renderer(), new PaperScheduler(plugin), new ListSourceRegistry());
        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);

        disabling.shutdown();

        assertThat(holderOfOpenWindow() instanceof MenuHolder)
                .as("a menu left open by a disable stays open with no engine behind it")
                .isFalse();
    }

    @Test
    void shutdownWithNoMenuOpenAnywhereIsNotAFailure() {
        assertThatCode(() -> menus.shutdown()).doesNotThrowAnyException();
        assertThat(scheduler.globalHops)
                .as("the sweep still runs, it just finds nothing to close")
                .isEqualTo(1);
    }
}

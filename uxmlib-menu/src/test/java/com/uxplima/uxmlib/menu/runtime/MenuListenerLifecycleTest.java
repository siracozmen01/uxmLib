package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuItemMark;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The three ways a menu ends that nothing drove before: the viewer closes it, the viewer quits with it open, and the
 * viewer dies with it open. All three are teardown, so none of them is visible in the window a player is looking at,
 * and all three cost something real when they are wrong. A refresh task that outlives its window re-renders an
 * inventory nobody is holding. A bottom-inventory menu that is not put back leaves a player looking at painted tiles
 * where their items were. A death that drops those tiles is a duplication bug: the tiles become real items, and the
 * viewer's own items are restored on respawn beside them.
 */
class MenuListenerLifecycleTest {

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

    private final AtomicLong now = new AtomicLong(1_000_000L);

    private Menus menus;

    private MenuListener listener;

    private SameThreadScheduler scheduler;

    private Player viewer;

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new SameThreadScheduler();
        menus = new Menus(renderer(), scheduler, new ListSourceRegistry());
        listener = new MenuListener(
                renderer(),
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                now::get,
                new PagedListSourceRegistry(),
                null,
                null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    private MenuHolder open(String hocon) {
        menus.registerSpec("menu", new MenuSpecLoader().parse(hocon));
        menus.open(viewer, "menu", null);
        return (MenuHolder) viewer.getOpenInventory().getTopInventory().getHolder();
    }

    /**
     * A real bottom-inventory open: the viewer is holding a diamond, the engine snapshots their 36 real slots onto
     * the holder and paints its own marked tiles over them. Every bottom test starts from here rather than from a
     * hand-set snapshot, so what is torn down is what an open actually built.
     */
    private MenuHolder openBottomHoldingADiamond() {
        viewer.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        // A bottom-inventory menu is always six rows, so its own slots start at raw 54 and raw 81 is the player
        // slot the diamond is sitting in. Painting anywhere else would leave the diamond visible and prove nothing.
        return open("rows = 3\nbottom-inventory = true\n"
                + "items { tile { slot = 81, material = GRAY_STAINED_GLASS_PANE, name = \" \" } }");
    }

    /** The drops a server would build from the viewer's inventory at the moment they die. */
    private List<ItemStack> dropsFromInventory() {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack item : viewer.getInventory().getStorageContents()) {
            if (item != null) {
                drops.add(item);
            }
        }
        return drops;
    }

    private void close() {
        listener.onClose(new InventoryCloseEvent(viewer.getOpenInventory()));
    }

    private void quit() {
        listener.onQuit(new PlayerQuitEvent(viewer, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
    }

    private PlayerDeathEvent death(List<ItemStack> drops) {
        PlayerDeathEvent event = new PlayerDeathEvent(
                viewer, DamageSource.builder(DamageType.GENERIC).build(), drops, 0, Component.empty(), false);
        listener.onDeath(event);
        return event;
    }

    // -- closing ----------------------------------------------------------------------------------------------

    @Test
    void closingAMenuStopsItsRefreshSoNoTimerRedrawsAWindowNobodyIsHolding() {
        MenuHolder holder = open("rows = 3");
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

        close();

        assertThat(cancelled[0]).isTrue();
    }

    @Test
    void closingTwiceCancelsOnceAndIsNoWorseForIt() {
        MenuHolder holder = open("rows = 3");
        int[] cancels = {0};
        holder.setRefreshHandle(new com.uxplima.uxmlib.scheduler.TaskHandle() {

            @Override
            public void cancel() {
                cancels[0]++;
            }

            @Override
            public boolean isCancelled() {
                return cancels[0] > 0;
            }
        });

        close();
        close();

        assertThat(cancels[0]).isEqualTo(1);
    }

    @Test
    void closingAWindowTheEngineDidNotOpenIsNotTouched() {
        Inventory other = Bukkit.createInventory(null, 27);
        viewer.openInventory(other);

        close();

        assertThat(viewer.getOpenInventory().getTopInventory()).isSameAs(other);
    }

    @Test
    void theCloseHookRunsOnceAndOnlyForTheMenuThatCarriesOne() {
        MenuHolder holder = open("rows = 3");
        int[] hooks = {0};
        holder.attachCloseHook(() -> hooks[0]++);

        close();
        close();

        assertThat(hooks[0])
                .as("a hook that fires twice steps the operator back two windows")
                .isEqualTo(1);
    }

    // -- quitting ---------------------------------------------------------------------------------------------

    @Test
    void quittingWithAMenuOpenStopsItsRefreshToo() {
        MenuHolder holder = open("rows = 3");
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

        quit();

        assertThat(cancelled[0]).isTrue();
    }

    @Test
    void anOpenTakesTheViewersRealItemsOntoTheHolderAndPaintsOverThem() {
        MenuHolder holder = openBottomHoldingADiamond();

        assertThat(MenuItemMark.isMarked(viewer.getInventory().getItem(0)))
                .as("the bottom canvas is painted with marked tiles, which is what the teardown paths key on")
                .isTrue();
        ItemStack[] snapshot = Objects.requireNonNull(holder.bottomSnapshot());
        assertThat(snapshot[0]).isEqualTo(new ItemStack(Material.DIAMOND, 5));
    }

    @Test
    void quittingRestoresTheRealBottomInventoryWithoutWaitingForATick() {
        MenuHolder holder = openBottomHoldingADiamond();

        quit();

        assertThat(viewer.getInventory().getItem(0))
                .as("a deferred restore would run after the player is gone and their data saved")
                .isEqualTo(new ItemStack(Material.DIAMOND, 5));
        assertThat(holder.bottomSnapshot())
                .as("the snapshot has to be cleared so the paired close restore does not put the items back twice")
                .isNull();
    }

    @Test
    void aQuitFollowedByACloseRestoresExactlyOnce() {
        MenuHolder holder = openBottomHoldingADiamond();

        quit();
        viewer.getInventory().setItem(0, new ItemStack(Material.STONE));
        close();

        assertThat(viewer.getInventory().getItem(0))
                .as("the deferred close restore must see a cleared snapshot and do nothing")
                .isEqualTo(new ItemStack(Material.STONE));
        assertThat(holder.bottomSnapshot()).isNull();
    }

    @Test
    void closingABottomMenuPutsTheRealItemsBack() {
        MenuHolder holder = openBottomHoldingADiamond();

        close();

        assertThat(viewer.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.DIAMOND, 5));
        assertThat(holder.bottomSnapshot()).isNull();
    }

    @Test
    void anOrdinaryMenuCarriesNoSnapshotAndAQuitLeavesTheBottomAlone() {
        open("rows = 3");
        ItemStack own = new ItemStack(Material.STONE, 3);
        viewer.getInventory().setItem(0, own);

        quit();

        assertThat(viewer.getInventory().getItem(0)).isEqualTo(own);
    }

    // -- dying ------------------------------------------------------------------------------------------------

    @Test
    void dyingWithABottomMenuOpenDropsTheRealItemsAndNotTheTiles() {
        openBottomHoldingADiamond();
        List<ItemStack> drops = dropsFromInventory();
        assertThat(drops)
                .as("the fixture only means something if the tiles really are in the drops")
                .isNotEmpty();

        death(drops);

        assertThat(drops)
                .as("a painted tile that drops becomes a real item the viewer never had")
                .noneMatch(MenuItemMark::isMarked);
        assertThat(drops).contains(new ItemStack(Material.DIAMOND, 5));
    }

    @Test
    void theSnapshotIsClearedSoTheCloseThatFollowsTheDeathRestoresNothing() {
        MenuHolder holder = openBottomHoldingADiamond();

        death(dropsFromInventory());

        assertThat(holder.bottomSnapshot())
                .as("the drop is the single disposition of the real items: restoring them too is the dupe")
                .isNull();

        viewer.getInventory().clear();
        close();

        assertThat(viewer.getInventory().getItem(0))
                .as("the respawn restore has to be a no-op, or the dropped items exist twice")
                .isNull();
    }

    @Test
    void anOrdinaryMenuLeavesTheDropsExactlyAsTheyWere() {
        open("rows = 3");
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(Material.STONE));
        drops.add(MenuItemMark.mark(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)));

        death(drops);

        assertThat(drops)
                .as("a menu with no bottom snapshot never put a tile into the real inventory, so none is stripped")
                .hasSize(2);
    }

    @Test
    void dyingWithNoMenuOpenLeavesTheDropsAlone() {
        viewer.openInventory(Bukkit.createInventory(null, 27));
        List<ItemStack> drops = new ArrayList<>();
        drops.add(MenuItemMark.mark(new ItemStack(Material.GRAY_STAINED_GLASS_PANE)));

        death(drops);

        assertThat(drops).hasSize(1);
    }
}

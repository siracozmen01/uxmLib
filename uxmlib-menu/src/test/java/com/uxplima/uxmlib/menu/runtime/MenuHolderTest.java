package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.render.RenderedSlot;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The one object an open menu hangs everything off, and the reason the click listener needs no player-keyed side
 * map. These tests are about ownership and about what a second call does: the holder outlives several redraws and
 * two close paths, so the interesting questions are whether it hands out its own state and whether a repeat is a
 * no-op or a second run.
 */
class MenuHolderTest {

    /** A handle that only remembers whether it was cancelled, which is the whole of what the holder asks of one. */
    private static final class CountingHandle implements TaskHandle {

        private int cancels;

        @Override
        public void cancel() {
            cancels++;
        }

        @Override
        public boolean isCancelled() {
            return cancels > 0;
        }
    }

    private Player viewer;

    private MenuSpec spec;

    private MenuHolder holder;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        spec = new MenuSpecLoader().parse("rows = 1\nitems { one { slot = 0, material = STONE, name = \"n\" } }");
        holder = new MenuHolder("menus/test", spec, MenuContext.of(viewer, null, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private RenderedSlot slot() {
        MenuItemSpec item = Objects.requireNonNull(spec.items().get("one"));
        return new RenderedSlot(item, null);
    }

    // -- the inventory ----------------------------------------------------------------------------------------

    /** Asked before the engine has built the window, which is a wiring order mistake and not an operator's doing. */
    @Test
    void askingForTheInventoryBeforeItIsAttachedNamesTheMenuInTheFailure() {
        assertThatIllegalStateException().isThrownBy(holder::getInventory).withMessageContaining("menus/test");
    }

    @Test
    void theAttachedInventoryIsTheOneHandedBack() {
        var inv = MockBukkit.getMock().createInventory(holder, 9);
        holder.attach(inv);

        assertThat(holder.getInventory()).isSameAs(inv);
    }

    // -- the click map ----------------------------------------------------------------------------------------

    @Test
    void aRecordedSlotIsFoundAtItsOwnIndexAndNowhereElse() {
        RenderedSlot recorded = slot();
        holder.recordSlot(3, recorded);

        assertThat(holder.clickAt(3)).contains(recorded);
        assertThat(holder.clickAt(4)).isEmpty();
    }

    /** A redraw clears first, so a slot that used to be a button cannot be clicked as one after it stops being drawn. */
    @Test
    void clearingTheClickMapMakesEveryPreviousSlotUnclickable() {
        holder.recordSlot(3, slot());
        holder.clearClickMap();

        assertThat(holder.clickAt(3)).isEmpty();
    }

    @Test
    void recordingTheSameSlotTwiceKeepsTheSecondBecauseARedrawOverwrites() {
        RenderedSlot second = slot();
        holder.recordSlot(3, slot());
        holder.recordSlot(3, second);

        assertThat(holder.clickAt(3)).contains(second);
    }

    // -- the resolved lists -----------------------------------------------------------------------------------

    @Test
    void theResolvedListsAreCopiedInSoTheCallerCannotChangeThemAfterwards() {
        Map<String, List<?>> source = new HashMap<>();
        source.put("warps", List.of("a", "b"));
        holder.setResolvedLists(source);

        source.put("warps", List.of("c"));

        assertThat(holder.resolvedLists()).containsOnlyKeys("warps");
        assertThat(holder.resolvedLists().get("warps")).isEqualTo(List.of("a", "b"));
    }

    @Test
    void theResolvedListsHandedOutCannotBeWrittenTo() {
        holder.setResolvedLists(Map.of("warps", List.of("a")));

        assertThatThrownBy(() -> holder.resolvedLists().put("other", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aMenuThatResolvedNoListStartsWithNoneRatherThanNull() {
        assertThat(holder.resolvedLists()).isEmpty();
    }

    // -- the per-list query state -----------------------------------------------------------------------------

    /** Paging, sorting and filtering have to survive the redraw between two clicks, so the state is kept per id. */
    @Test
    void oneListIdKeepsOneStateAcrossEveryAsk() {
        ListQueryState first = holder.queryState("warps", List.of("name"));
        first.page(2);

        assertThat(holder.queryState("warps", List.of("name"))).isSameAs(first);
        assertThat(holder.queryState("warps", List.of("name")).page()).isEqualTo(2);
    }

    @Test
    void twoListIdsKeepTwoIndependentStates() {
        ListQueryState warps = holder.queryState("warps", List.of("name"));
        ListQueryState homes = holder.queryState("homes", List.of("name"));

        assertThat(homes).isNotSameAs(warps);
    }

    /** The sorts come from the spec, so the first ask decides them and a later ask cannot quietly replace them. */
    @Test
    void theSortsOfferedOnALaterAskDoNotReplaceTheOnesTheStateWasBuiltWith() {
        holder.queryState("warps", List.of("name"));

        assertThat(holder.queryState("warps", List.of("distance")).sort()).isEqualTo("name");
    }

    // -- the bottom snapshot ----------------------------------------------------------------------------------

    @Test
    void theSnapshotArrayIsCopiedOnTheWayInAndOnTheWayOut() {
        ItemStack[] saved = {new ItemStack(Material.STONE)};
        holder.setBottomSnapshot(saved);

        saved[0] = new ItemStack(Material.DIRT);
        ItemStack[] readBack = Objects.requireNonNull(holder.bottomSnapshot());
        readBack[0] = new ItemStack(Material.SAND);

        assertThat(Objects.requireNonNull(holder.bottomSnapshot())[0]).isEqualTo(new ItemStack(Material.STONE));
    }

    @Test
    void clearingTheSnapshotIsHowASecondCloseIsStoppedFromRestoringTwice() {
        holder.setBottomSnapshot(new ItemStack[] {new ItemStack(Material.STONE)});
        holder.setBottomSnapshot(null);

        assertThat(holder.bottomSnapshot()).isNull();
    }

    @Test
    void anOrdinaryMenuHasNoSnapshotAtAll() {
        assertThat(holder.bottomSnapshot()).isNull();
    }

    // -- the close hook ---------------------------------------------------------------------------------------

    @Test
    void aMenuWithNoCloseHookIsAHarmlessNoOp() {
        holder.fireCloseHook();
    }

    /** A close followed by a quit close both reach this, and the preview must step back exactly once. */
    @Test
    void aCloseHookRunsOnceHoweverManyTimesTheCloseIsFired() {
        int[] runs = {0};
        holder.attachCloseHook(() -> runs[0]++);

        holder.fireCloseHook();
        holder.fireCloseHook();

        assertThat(runs[0]).isEqualTo(1);
    }

    /**
     * The hook is cleared before it runs rather than after, so a hook that closes another window (which is what the
     * preview's hook does) cannot re-enter this and run itself a second time.
     */
    @Test
    void aCloseHookThatFiresTheCloseHookAgainDoesNotRunTwice() {
        int[] runs = {0};
        holder.attachCloseHook(() -> {
            runs[0]++;
            holder.fireCloseHook();
        });

        holder.fireCloseHook();

        assertThat(runs[0]).isEqualTo(1);
    }

    // -- the refresh task -------------------------------------------------------------------------------------

    @Test
    void cancellingTheRefreshCancelsTheHandleOnce() {
        CountingHandle handle = new CountingHandle();
        holder.setRefreshHandle(handle);

        holder.cancelRefresh();
        holder.cancelRefresh();

        assertThat(handle.cancels).isEqualTo(1);
    }

    @Test
    void cancellingARefreshThatWasNeverStartedIsANoOp() {
        holder.cancelRefresh();
    }

    // -- the attached states ----------------------------------------------------------------------------------

    @Test
    void aSpecMenuCarriesNoneOfTheEditorPathStates() {
        assertThat(holder.editor()).isEmpty();
        assertThat(holder.confirm()).isEmpty();
        assertThat(holder.selector()).isEmpty();
        assertThat(holder.listView()).isEmpty();
        assertThat(holder.gridView()).isEmpty();
    }

    // -- the live context -------------------------------------------------------------------------------------

    /** The page changes as the viewer flips, and the holder is where the new context is kept for the next redraw. */
    @Test
    void thePageOfTheOpenIsWhateverTheContextWasLastSetTo() {
        holder.setCtx(MenuContext.of(viewer, null, 4));

        assertThat(holder.ctx().page()).isEqualTo(4);
    }

    @Test
    void theSpecAndItsIdAreWhatTheHolderWasBuiltWith() {
        assertThat(holder.specId()).isEqualTo("menus/test");
        assertThat(holder.spec()).isSameAs(spec);
    }
}

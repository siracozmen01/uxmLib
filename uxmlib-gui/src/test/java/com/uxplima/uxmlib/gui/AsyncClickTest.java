package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.item.GuiAction;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The async + declarative click pipeline (item 38) and the re-check (item 39) wired together. The async
 * path is driven by a synchronous {@link Scheduler} double that records whether the entity family was used,
 * so the {@code isDone()} fast-path (inline, no hop) and the off-thread path (responses applied through the
 * scheduler) are both observable without a live server.
 */
class AsyncClickTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A Scheduler that runs entity tasks inline and records that it was asked to. */
    static final class RecordingScheduler implements Scheduler {
        boolean entityUsed;

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            entityUsed = true;
            task.run();
            return FINISHED;
        }

        @Override
        public TaskHandle global(Runnable task) {
            task.run();
            return FINISHED;
        }

        private static final TaskHandle FINISHED = new TaskHandle() {
            @Override
            public void cancel() {}

            @Override
            public boolean isCancelled() {
                return true;
            }
        };

        @Override
        public TaskHandle globalLater(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle region(Location location, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle async(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }
    }

    private InventoryClickEvent clickWithIcon(PlayerMock player, Gui gui, ItemStack icon) {
        gui.getInventory().setItem(0, icon);
        InventoryView view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        event.setCurrentItem(icon);
        return event;
    }

    @Test
    void aSyncHandlerAppliesInlineWithoutAnEntityHop() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ItemStack icon = new ItemStack(Material.STONE);
        boolean[] ran = {false};
        GuiAction.Responding action = GuiItem.respondingSync(ctx -> List.of(GuiResponse.run(() -> ran[0] = true)));
        InventoryClickEvent event = clickWithIcon(player, gui, icon);
        RecordingScheduler scheduler = new RecordingScheduler();

        AsyncClick.dispatch(action, gui, event, scheduler, icon, t -> {});

        assertThat(ran[0]).isTrue();
        assertThat(scheduler.entityUsed).isFalse(); // isDone() fast-path: applied inline, no scheduler hop
    }

    @Test
    void anAsyncHandlerAppliesItsResponsesThroughTheEntityScheduler() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ItemStack icon = new ItemStack(Material.STONE);
        CompletableFuture<List<GuiResponse>> pending = new CompletableFuture<>();
        boolean[] ran = {false};
        GuiAction.Responding action = GuiItem.respondingAsync(ctx -> pending);
        InventoryClickEvent event = clickWithIcon(player, gui, icon);
        RecordingScheduler scheduler = new RecordingScheduler();

        AsyncClick.dispatch(action, gui, event, scheduler, icon, t -> {});
        assertThat(ran[0]).isFalse(); // nothing applied yet — the future is still pending
        pending.complete(List.of(GuiResponse.run(() -> ran[0] = true)));

        assertThat(ran[0]).isTrue();
        assertThat(scheduler.entityUsed).isTrue(); // off-thread result marshalled back through the scheduler
    }

    @Test
    void aDeferredResponseAppliesToTheViewerNotTheRecycledEvent() {
        // A response that touches view/cursor state must resolve against the viewer's CURRENT open view, not
        // the click event (which the server has already resolved and recycled by the time the deferred
        // response settles a tick later). Driving it through the async path proves the snapshot is used.
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ItemStack icon = new ItemStack(Material.STONE);
        CompletableFuture<List<GuiResponse>> pending = new CompletableFuture<>();
        GuiAction.Responding action = GuiItem.respondingAsync(ctx -> pending);
        InventoryClickEvent event = clickWithIcon(player, gui, icon);
        RecordingScheduler scheduler = new RecordingScheduler();

        AsyncClick.dispatch(action, gui, event, scheduler, icon, t -> {});
        pending.complete(List.of(GuiResponse.replaceCursor(new ItemStack(Material.GOLD_INGOT, 3))));

        assertThat(scheduler.entityUsed).isTrue();
        assertThat(player.getOpenInventory().getCursor().getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(player.getOpenInventory().getCursor().getAmount()).isEqualTo(3);
    }

    @Test
    void aStaleIconSkipsTheHandlerEntirely() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ItemStack clickedIcon = new ItemStack(Material.STONE);
        boolean[] ran = {false};
        GuiAction.Responding action = GuiItem.respondingSync(ctx -> List.of(GuiResponse.run(() -> ran[0] = true)));
        InventoryClickEvent event = clickWithIcon(player, gui, clickedIcon);
        RecordingScheduler scheduler = new RecordingScheduler();

        // The slot now resolves to a different icon than the one the player clicked (animation advanced).
        ItemStack currentIcon = new ItemStack(Material.GOLD_BLOCK);
        AsyncClick.dispatch(action, gui, event, scheduler, currentIcon, t -> {});

        assertThat(ran[0]).isFalse(); // re-check failed: the action is skipped (item 39)
    }

    @Test
    void withoutASchedulerAnAlreadyDoneFutureStillApplies() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ItemStack icon = new ItemStack(Material.STONE);
        boolean[] ran = {false};
        GuiAction.Responding action = GuiItem.respondingSync(ctx -> List.of(GuiResponse.run(() -> ran[0] = true)));
        InventoryClickEvent event = clickWithIcon(player, gui, icon);

        AsyncClick.dispatch(action, gui, event, null, icon, t -> {});

        assertThat(ran[0]).isTrue();
    }

    @Test
    void aHandlerThatThrowsRoutesTheErrorAndDoesNotPropagate() {
        SimpleGui gui = Guis.gui().rows(1).build();
        PlayerMock player = MockBukkit.getMock().addPlayer();
        ItemStack icon = new ItemStack(Material.STONE);
        java.util.List<Throwable> errors = new java.util.ArrayList<>();
        GuiAction.Responding action =
                GuiItem.respondingAsync(ctx -> CompletableFuture.failedFuture(new IllegalStateException("boom")));
        InventoryClickEvent event = clickWithIcon(player, gui, icon);
        RecordingScheduler scheduler = new RecordingScheduler();

        AsyncClick.dispatch(action, gui, event, scheduler, icon, errors::add);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(IllegalStateException.class);
    }
}

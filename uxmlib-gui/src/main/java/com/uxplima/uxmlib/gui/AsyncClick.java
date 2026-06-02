package com.uxplima.uxmlib.gui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.item.GuiAction;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * The declarative + async click pipeline (item 38) gated by the anti-desync re-check (item 39). Given a
 * {@link GuiAction.Responding} action and the click event, it:
 *
 * <ol>
 *   <li>re-checks that the slot still holds the icon the click targeted (else it skips the action);</li>
 *   <li>snapshots the click into an immutable {@link ClickContext} and runs the handler;</li>
 *   <li>applies the resulting responses — inline when the future is already complete (the {@code isDone()}
 *       fast-path, no scheduler hop), otherwise off-thread with the responses marshalled back onto the
 *       viewer's region thread through the library {@link Scheduler}.</li>
 * </ol>
 *
 * <p>Only the response <em>application</em> is routed through the scheduler; the heavy work runs wherever
 * the handler put it. A handler that fails routes its cause through {@code onError} instead of throwing, so
 * the failure is logged once and never swallowed. Pattern from AnvilGUI's async {@code ClickHandler} (MIT):
 * one {@code CompletableFuture} path for sync and async, with an {@code isDone()} fast-path.
 */
final class AsyncClick {

    private AsyncClick() {}

    /**
     * Dispatch {@code action} for {@code event}. {@code currentIcon} is the icon currently resolved for the
     * viewer at the clicked slot (used for the re-check); {@code scheduler} may be {@code null} (no
     * Scheduler installed), in which case responses are applied inline.
     */
    static void dispatch(
            GuiAction.Responding action,
            Gui gui,
            InventoryClickEvent event,
            @Nullable Scheduler scheduler,
            @Nullable ItemStack currentIcon,
            Consumer<Throwable> onError) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(gui, "gui");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(onError, "onError");
        if (!ClickRecheck.stillMatches(orAir(event.getCurrentItem()), currentIcon)) {
            return; // the slot changed between render and click: skip, leaving the cancel in place
        }
        // Snapshot the click now, while the event is still live; responses are applied against this snapshot
        // (and the viewer's current open view), never the recycled event, which may settle a tick later.
        ClickContext context = ClickContext.of(event);
        CompletableFuture<List<GuiResponse>> future = run(action, context, onError);
        if (future.isDone()) {
            applyNow(future, gui, context, onError); // fast-path: handler ran synchronously, apply inline
        } else {
            applyLater(future, gui, context, scheduler, onError);
        }
    }

    /** Invoke the handler, converting a thrown exception into a failed future so one code path handles it. */
    private static CompletableFuture<List<GuiResponse>> run(
            GuiAction.Responding action, ClickContext context, Consumer<Throwable> onError) {
        try {
            CompletableFuture<List<GuiResponse>> future = action.handler().apply(context);
            return future == null ? CompletableFuture.completedFuture(List.of()) : future;
        } catch (RuntimeException error) {
            onError.accept(error);
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /** Apply an already-complete future inline on the current (region) thread. */
    private static void applyNow(
            CompletableFuture<List<GuiResponse>> future, Gui gui, ClickContext context, Consumer<Throwable> onError) {
        try {
            GuiResponses.apply(future.join(), gui, context);
        } catch (CompletionException error) {
            onError.accept(unwrap(error));
        }
    }

    /**
     * Apply a still-pending future when it settles, marshalling the response application back onto the
     * viewer's region thread through {@code scheduler}. Without a scheduler the application runs in the
     * completing thread (the no-Scheduler degradation the listener already uses for the simple path).
     */
    private static void applyLater(
            CompletableFuture<List<GuiResponse>> future,
            Gui gui,
            ClickContext context,
            @Nullable Scheduler scheduler,
            Consumer<Throwable> onError) {
        var unused = future.whenComplete((responses, error) -> {
            if (error != null) {
                onError.accept(unwrap(error));
            } else {
                onRegionThread(scheduler, context.viewer(), () -> GuiResponses.apply(responses, gui, context));
            }
        });
    }

    private static void onRegionThread(@Nullable Scheduler scheduler, org.bukkit.entity.Player viewer, Runnable task) {
        if (scheduler == null) {
            task.run();
        } else {
            scheduler.entity(viewer, task);
        }
    }

    private static ItemStack orAir(@Nullable ItemStack item) {
        return item == null ? new ItemStack(org.bukkit.Material.AIR) : item;
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}

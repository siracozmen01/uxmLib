package com.uxplima.uxmlib.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

/**
 * Applies a list of {@link GuiResponse}s — the declarative result of a click handler — in order. This is
 * the single place where a declarative click touches Bukkit, so handlers can stay pure functions and the
 * side effects live here, on the viewer's region thread.
 *
 * <p>Splitting application out of {@link GuiResponse} keeps the response model a small value type and the
 * "how to apply each kind" switch in one home that mirrors the sealed set, so adding a response kind forces
 * a matching application branch.
 *
 * <p>Application is driven from the immutable {@link ClickContext} snapshot, not the live click event. When
 * the slot action is deferred a tick (the Scheduler path), the original {@code InventoryClickEvent} has
 * already been resolved and recycled by the server, so reading or writing its view/cursor would touch dead
 * state; the snapshot keeps the viewer and the response resolves against the viewer's <em>current</em> open
 * view instead.
 */
final class GuiResponses {

    private GuiResponses() {}

    /** Apply every response in {@code responses} to {@code gui} for {@code context}'s viewer, in order. */
    static void apply(List<GuiResponse> responses, Gui gui, ClickContext context) {
        Objects.requireNonNull(responses, "responses");
        Objects.requireNonNull(gui, "gui");
        Objects.requireNonNull(context, "context");
        Player viewer = context.viewer();
        for (GuiResponse response : responses) {
            apply(Objects.requireNonNull(response, "response"), gui, viewer);
        }
    }

    private static void apply(GuiResponse response, Gui gui, Player viewer) {
        switch (response) {
            case GuiResponse.Close ignored -> viewer.closeInventory();
            case GuiResponse.Open open -> open.gui().open(viewer);
            case GuiResponse.Refresh ignored -> gui.refresh();
            case GuiResponse.UpdateItem update -> gui.set(update.slot(), update.item());
            case GuiResponse.ReplaceCursor replace -> viewer.getOpenInventory().setCursor(replace.cursor());
            case GuiResponse.PlaySound play -> viewer.playSound(play.sound());
            case GuiResponse.Run run -> run.task().run();
            case GuiResponse.Nothing ignored -> {
                // Deliberately no effect: a handler that opts out still returns a response.
            }
        }
    }
}

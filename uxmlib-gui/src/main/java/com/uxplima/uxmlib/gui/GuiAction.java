package com.uxplima.uxmlib.gui;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * What happens when a {@link GuiItem} is clicked. A sealed pair — {@link Run} carries a handler,
 * {@link None} does nothing — so click routing pattern-matches over a closed set instead of guarding a
 * nullable callback, and a "no action" slot is explicit rather than a silent null.
 */
public sealed interface GuiAction permits GuiAction.Run, GuiAction.None {

    /** Run the action for {@code event}. The framework has already cancelled the event by default. */
    void accept(InventoryClickEvent event);

    /** An action that runs a handler. */
    record Run(Consumer<InventoryClickEvent> handler) implements GuiAction {
        public Run {
            Objects.requireNonNull(handler, "handler");
        }

        @Override
        public void accept(InventoryClickEvent event) {
            handler.accept(event);
        }
    }

    /** The do-nothing action; the shared {@link #INSTANCE} avoids allocating per display item. */
    record None() implements GuiAction {
        public static final None INSTANCE = new None();

        @Override
        public void accept(InventoryClickEvent event) {
            // Intentionally empty: a display item has no behaviour.
        }
    }
}

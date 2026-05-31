package com.uxplima.uxmlib.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

/**
 * Entry point for the menu framework: builders for the two menu kinds and the one-time listener install.
 *
 * <pre>{@code
 * Guis.install(plugin);                 // once, on enable
 * SimpleGui menu = Guis.gui().title(Component.text("Menu")).rows(3).build();
 * menu.set(13, GuiItem.button(icon, e -> e.getWhoClicked().sendMessage("clicked")));
 * menu.open(player);
 * }</pre>
 */
public final class Guis {

    private Guis() {}

    /** Register the single {@link GuiListener} so menu events are routed. Call once per plugin enable. */
    public static void install(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        Bukkit.getPluginManager().registerEvents(new GuiListener(), plugin);
    }

    /** A builder for a single-page {@link SimpleGui}. */
    public static SimpleBuilder gui() {
        return new SimpleBuilder();
    }

    /** A builder for a {@link PaginatedGui}. */
    public static PaginatedBuilder paginated() {
        return new PaginatedBuilder();
    }

    /** Shared builder state with a self-returning fluent API. */
    abstract static class Builder<B extends Builder<B>> {
        Component title = Component.empty();
        int rows = 1;

        @SuppressWarnings("unchecked") // self-type cast; safe because B is always the concrete subtype
        final B self() {
            return (B) this;
        }

        /** Set the menu title. */
        public B title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return self();
        }

        /** Set the number of rows (1..6). */
        public B rows(int rows) {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("rows must be 1..6");
            }
            this.rows = rows;
            return self();
        }
    }

    /** Builder for {@link SimpleGui}. */
    public static final class SimpleBuilder extends Builder<SimpleBuilder> {
        private SimpleBuilder() {}

        /** Build the menu. */
        public SimpleGui build() {
            return new SimpleGui(title, rows);
        }
    }

    /** Builder for {@link PaginatedGui}. */
    public static final class PaginatedBuilder extends Builder<PaginatedBuilder> {
        private @org.jspecify.annotations.Nullable List<Integer> contentSlots;

        private PaginatedBuilder() {}

        /** The slots that page items fill. Defaults to every slot except the bottom row. */
        public PaginatedBuilder contentSlots(List<Integer> slots) {
            Objects.requireNonNull(slots, "slots");
            this.contentSlots = List.copyOf(slots);
            return this;
        }

        /** Build the menu. */
        public PaginatedGui build() {
            List<Integer> slots = contentSlots != null ? contentSlots : defaultContentSlots(rows);
            return new PaginatedGui(title, rows, slots);
        }

        private static List<Integer> defaultContentSlots(int rows) {
            // Every slot except the bottom row, which is left free for navigation buttons.
            int contentRows = rows > 1 ? rows - 1 : rows;
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < contentRows * 9; slot++) {
                slots.add(slot);
            }
            return slots;
        }
    }
}

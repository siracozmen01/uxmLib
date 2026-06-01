package com.uxplima.uxmlib.gui;

import java.time.Duration;
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

    private static @org.jspecify.annotations.Nullable GuiRegistry registry;

    /** Register the single {@link GuiListener} so menu events are routed. Call once per plugin enable. */
    public static void install(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        Bukkit.getPluginManager().registerEvents(new GuiListener(), plugin);
    }

    /**
     * Install the listener and wire a {@link com.uxplima.uxmlib.scheduler.Scheduler} so menus with
     * animated items or {@code autoRefresh} can tick. Use this overload to enable animation.
     */
    public static void install(Plugin plugin, com.uxplima.uxmlib.scheduler.Scheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        Bukkit.getPluginManager().registerEvents(new GuiListener(), plugin);
        registry = new GuiRegistry(scheduler);
    }

    /** The animation registry, present only when {@link #install(Plugin, com.uxplima.uxmlib.scheduler.Scheduler)} was used. */
    static @org.jspecify.annotations.Nullable GuiRegistry registry() {
        return registry;
    }

    /** A builder for a single-page {@link SimpleGui}. */
    public static SimpleBuilder gui() {
        return new SimpleBuilder();
    }

    /** A builder for a {@link PaginatedGui}. */
    public static PaginatedBuilder paginated() {
        return new PaginatedBuilder();
    }

    /** A builder for a non-chest {@link SimpleGui} of the given {@link GuiType} (hopper, dispenser, …). */
    public static TypedBuilder typed(GuiType type) {
        return new TypedBuilder(type);
    }

    /** A builder for a {@link StorageGui} that holds real items and keeps them across opens. */
    public static StorageBuilder storage() {
        return new StorageBuilder();
    }

    /** A builder for a {@link ScrollingGui} that scrolls its content in {@code scrollType} direction. */
    public static ScrollingBuilder scrolling(ScrollType scrollType) {
        return new ScrollingBuilder(scrollType);
    }

    /** Shared builder state with a self-returning fluent API. */
    abstract static class Builder<B extends Builder<B>> {
        Component title = Component.empty();
        int rows = 1;
        final java.util.Set<InteractionModifier> allowed = java.util.EnumSet.noneOf(InteractionModifier.class);

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

        /** Allow one or more interactions the menu would otherwise cancel (e.g. for a storage menu). */
        public B allow(InteractionModifier... modifiers) {
            Objects.requireNonNull(modifiers, "modifiers");
            for (InteractionModifier modifier : modifiers) {
                allowed.add(Objects.requireNonNull(modifier, "modifier"));
            }
            return self();
        }

        private final List<java.util.function.Consumer<Gui>> postBuild = new ArrayList<>();

        @org.jspecify.annotations.Nullable Duration autoRefresh;

        /** Run {@code action} on the menu right after it is built (to add items, set handlers, etc.). */
        public B apply(java.util.function.Consumer<Gui> action) {
            postBuild.add(Objects.requireNonNull(action, "action"));
            return self();
        }

        /** Re-render the menu every {@code interval} while it is open (needs the Scheduler-aware install). */
        public B autoRefresh(Duration interval) {
            this.autoRefresh = Objects.requireNonNull(interval, "interval");
            return self();
        }

        final <G extends AbstractGui> G finish(G gui) {
            for (InteractionModifier modifier : allowed) {
                gui.allow(modifier);
            }
            gui.autoRefresh(autoRefresh);
            for (java.util.function.Consumer<Gui> action : postBuild) {
                action.accept(gui);
            }
            return gui;
        }
    }

    /** Builder for {@link SimpleGui}. */
    public static final class SimpleBuilder extends Builder<SimpleBuilder> {
        private SimpleBuilder() {}

        /** Build the menu. */
        public SimpleGui build() {
            return finish(new SimpleGui(title, rows));
        }
    }

    /** Builder for a non-chest {@link SimpleGui} sized by its {@link GuiType}; rows do not apply. */
    public static final class TypedBuilder {
        private final GuiType type;
        private Component title = Component.empty();

        private TypedBuilder(GuiType type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        /** Set the menu title. */
        public TypedBuilder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** Build the menu. */
        public SimpleGui build() {
            return new SimpleGui(title, type);
        }
    }

    /** Builder for a {@link StorageGui}. */
    public static final class StorageBuilder extends Builder<StorageBuilder> {
        private StorageBuilder() {}

        /** Build the storage menu. */
        public StorageGui build() {
            return finish(new StorageGui(title, rows));
        }
    }

    /** Builder for a {@link ScrollingGui}. */
    public static final class ScrollingBuilder extends Builder<ScrollingBuilder> {
        private final ScrollType scrollType;

        private ScrollingBuilder(ScrollType scrollType) {
            this.scrollType = Objects.requireNonNull(scrollType, "scrollType");
        }

        /** Build the scrolling menu. */
        public ScrollingGui build() {
            return finish(new ScrollingGui(title, rows, scrollType));
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
            return finish(new PaginatedGui(title, rows, slots));
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

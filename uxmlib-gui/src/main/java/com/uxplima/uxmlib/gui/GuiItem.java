package com.uxplima.uxmlib.gui;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * An icon in a {@link Gui}: an {@link ItemStack} to show and a {@link GuiAction} to run on click. A
 * sealed set of kinds, resolved per viewer through a {@link RenderContext} so the same slot can look and
 * behave differently for each player:
 *
 * <ul>
 *   <li>{@link Static} — one fixed icon and action for everyone (the common case).</li>
 *   <li>{@link Dynamic} — the icon is computed from the viewer's {@link RenderContext} at render time.</li>
 *   <li>{@link Stateful} — the first of several named states whose condition matches the viewer wins.</li>
 *   <li>{@link Animated} — cycles through frames on a timer while the menu is open.</li>
 * </ul>
 *
 * <p>Most callers use the factories ({@link #button}, {@link #display}, {@link #dynamic}); the menu
 * resolves a kind with {@link #icon(RenderContext)} and {@link #action(RenderContext)} during render.
 */
public sealed interface GuiItem permits GuiItem.Static, GuiItem.Dynamic, GuiItem.Stateful, GuiItem.Animated {

    /** The icon to show this viewer. */
    ItemStack icon(RenderContext context);

    /** The action to run when this viewer clicks it. */
    GuiAction action(RenderContext context);

    /** A clickable button: a fixed icon plus a handler run on click. */
    static GuiItem button(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        return new Static(item, new GuiAction.Run(onClick));
    }

    /** A display-only icon with no click behaviour. */
    static GuiItem display(ItemStack item) {
        return new Static(item, GuiAction.None.INSTANCE);
    }

    /** An icon computed per viewer, with no click behaviour. */
    static GuiItem dynamic(Function<RenderContext, ItemStack> icon) {
        return new Dynamic(icon, ctx -> GuiAction.None.INSTANCE);
    }

    /** An icon computed per viewer, plus a handler run on click. */
    static GuiItem dynamic(Function<RenderContext, ItemStack> icon, Consumer<InventoryClickEvent> onClick) {
        return new Dynamic(icon, ctx -> new GuiAction.Run(onClick));
    }

    /** Start building a multi-state item whose first matching state wins per viewer. */
    static Stateful.Builder stateful() {
        return new Stateful.Builder();
    }

    /** A display-only item that cycles through {@code frames} every {@code interval} while open. */
    static GuiItem animated(java.util.List<ItemStack> frames, java.time.Duration interval) {
        return new Animated(frames, interval, GuiAction.None.INSTANCE);
    }

    /** An animated item that also runs {@code onClick} when clicked. */
    static GuiItem animated(
            java.util.List<ItemStack> frames, java.time.Duration interval, Consumer<InventoryClickEvent> onClick) {
        return new Animated(frames, interval, new GuiAction.Run(onClick));
    }

    /** A fixed icon and action, identical for every viewer. */
    record Static(ItemStack item, GuiAction guiAction) implements GuiItem {
        public Static {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(guiAction, "guiAction");
        }

        @Override
        public ItemStack icon(RenderContext context) {
            return item;
        }

        @Override
        public GuiAction action(RenderContext context) {
            return guiAction;
        }
    }

    /** An icon computed from the viewer's context, with a context-derived action. */
    record Dynamic(Function<RenderContext, ItemStack> iconFn, Function<RenderContext, GuiAction> actionFn)
            implements GuiItem {
        public Dynamic {
            Objects.requireNonNull(iconFn, "iconFn");
            Objects.requireNonNull(actionFn, "actionFn");
        }

        @Override
        public ItemStack icon(RenderContext context) {
            return Objects.requireNonNull(iconFn.apply(context), "dynamic icon");
        }

        @Override
        public GuiAction action(RenderContext context) {
            return Objects.requireNonNull(actionFn.apply(context), "dynamic action");
        }
    }

    /** One state of a {@link Stateful} item: the condition that selects it, its icon, and its action. */
    record State(java.util.function.Predicate<RenderContext> visibleFor, ItemStack icon, GuiAction guiAction) {
        public State {
            Objects.requireNonNull(visibleFor, "visibleFor");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(guiAction, "guiAction");
        }
    }

    /**
     * An item with several named states; the first whose condition passes for the viewer is shown. With
     * no match the slot renders empty for that viewer. Build one with {@link GuiItem#stateful()}.
     */
    record Stateful(java.util.List<State> states) implements GuiItem {
        public Stateful {
            Objects.requireNonNull(states, "states");
            states = java.util.List.copyOf(states);
        }

        @Override
        public ItemStack icon(RenderContext context) {
            State match = resolve(context);
            return match == null ? new ItemStack(org.bukkit.Material.AIR) : match.icon();
        }

        @Override
        public GuiAction action(RenderContext context) {
            State match = resolve(context);
            return match == null ? GuiAction.None.INSTANCE : match.guiAction();
        }

        /** The state this viewer currently sees, or {@code null} when none match. */
        public @org.jspecify.annotations.Nullable State resolve(RenderContext context) {
            for (State state : states) {
                if (state.visibleFor().test(context)) {
                    return state;
                }
            }
            return null;
        }

        /** Fluent builder for ordered states. */
        public static final class Builder {
            private final java.util.List<State> states = new java.util.ArrayList<>();

            /** Add a state shown when {@code condition} passes, falling through to later states otherwise. */
            public Builder state(
                    java.util.function.Predicate<RenderContext> condition,
                    ItemStack icon,
                    Consumer<InventoryClickEvent> onClick) {
                states.add(new State(condition, icon, new GuiAction.Run(onClick)));
                return this;
            }

            /** Add a display-only state (no click behaviour). */
            public Builder display(java.util.function.Predicate<RenderContext> condition, ItemStack icon) {
                states.add(new State(condition, icon, GuiAction.None.INSTANCE));
                return this;
            }

            /** Build the stateful item. */
            public Stateful build() {
                if (states.isEmpty()) {
                    throw new IllegalStateException("a stateful item needs at least one state");
                }
                return new Stateful(states);
            }
        }
    }

    /**
     * An item that cycles through {@code frames} every {@code interval} while the menu is open and
     * auto-refreshes. The {@link GuiAction} is constant across frames. The frame shown is chosen from the
     * menu's tick count so all viewers stay in sync.
     */
    record Animated(java.util.List<ItemStack> frames, java.time.Duration interval, GuiAction guiAction)
            implements GuiItem {
        public Animated {
            Objects.requireNonNull(frames, "frames");
            Objects.requireNonNull(interval, "interval");
            Objects.requireNonNull(guiAction, "guiAction");
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("frames must not be empty");
            }
            frames = java.util.List.copyOf(frames);
        }

        @Override
        public ItemStack icon(RenderContext context) {
            int frame = frameIndex(context.gui().ticks());
            return frames.get(frame);
        }

        @Override
        public GuiAction action(RenderContext context) {
            return guiAction;
        }

        private int frameIndex(long ticks) {
            long ticksPerFrame = Math.max(1L, interval.toMillis() / 50L);
            return (int) ((ticks / ticksPerFrame) % frames.size());
        }
    }
}

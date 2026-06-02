package com.uxplima.uxmlib.gui.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.item.GuiAction;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.gui.item.RenderContext;
import org.jspecify.annotations.Nullable;

/**
 * A registry of named conditions a config-defined menu can reference to choose which of an item's several
 * states a given viewer sees. Code owns the predicate — each condition is a {@link Predicate} over the
 * per-viewer {@link RenderContext} registered under a key — and an operator references those keys from a
 * config file, exactly as {@link MenuActions} maps click behaviour. A menu item lists ordered named states;
 * the first whose condition passes for the viewer renders (see {@link #statefulOf}).
 *
 * <p>An {@code always} condition is registered out of the box for the catch-all last state; register your
 * own (permission checks, online status, a balance threshold) with {@link #register}.
 */
public final class MenuConditions {

    private final Map<String, Predicate<RenderContext>> conditions = new HashMap<>();

    /** A registry pre-populated with the catch-all {@code always} condition. */
    public MenuConditions() {
        register("always", ctx -> true);
    }

    /** Register {@code condition} under {@code name} (replacing any existing one). Returns this. */
    public MenuConditions register(String name, Predicate<RenderContext> condition) {
        conditions.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(condition, "condition"));
        return this;
    }

    /** The condition registered under {@code name}, or {@code null} if none. */
    public @Nullable Predicate<RenderContext> get(String name) {
        Objects.requireNonNull(name, "name");
        return conditions.get(name);
    }

    /** The condition registered under {@code name}, or an {@link IllegalArgumentException} if none. */
    public Predicate<RenderContext> require(String name) {
        Predicate<RenderContext> condition = get(name);
        if (condition == null) {
            throw new IllegalArgumentException("unknown menu condition: " + name);
        }
        return condition;
    }

    /**
     * One ordered state of a multi-state menu item: its operator-facing {@code name} (for diagnostics), the
     * {@code condition} that selects it, the {@code icon} it shows, and the {@code action} run on click.
     */
    public record NamedState(String name, Predicate<RenderContext> condition, ItemStack icon, GuiAction action) {
        public NamedState {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(action, "action");
        }
    }

    /**
     * The pure selection core: fold an ordered list of named states into a {@link GuiItem.Stateful}, whose
     * {@link GuiItem.Stateful#resolve} already renders the first state whose condition passes for the viewer.
     */
    public static GuiItem.Stateful statefulOf(List<NamedState> states) {
        Objects.requireNonNull(states, "states");
        if (states.isEmpty()) {
            throw new IllegalArgumentException("a multi-state item needs at least one state");
        }
        List<GuiItem.State> resolved = states.stream()
                .map(state -> new GuiItem.State(state.condition(), state.icon(), state.action()))
                .toList();
        return new GuiItem.Stateful(resolved);
    }
}

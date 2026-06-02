package com.uxplima.uxmlib.condition.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An ordered sequence of {@link Action}s, parsed once from a list of config strings and run together against
 * one {@link ActionContext}. {@link #run(ActionContext)} fires each action in declaration order; this is the
 * unit a condition's "on success" / "on failure" hook drives, and the unit a menu click or event handler runs.
 *
 * <p>The list does not schedule anything itself. {@link #hasAsyncActions()} reports only whether <em>any</em>
 * action is async; it is <b>not</b> a licence to run the whole list off-thread — a sync action (a {@code
 * [console]}/{@code [player]} command, a {@code [close]}) must still execute on the main/region thread even
 * when the list also contains an async one. A scheduler-aware driver should route per lane using {@link
 * #syncActions()} (run on the owning thread) and {@link #asyncActions()} (may hop), each of which preserves
 * declaration order within its lane. Running everything in order on the calling thread keeps the engine pure
 * and unit-testable; production wiring routes the call through the library {@code Scheduler}.
 */
public final class ActionList {

    private final List<Action> actions;

    private ActionList(List<Action> actions) {
        this.actions = List.copyOf(actions);
    }

    /** Parse a list of config action strings once into an immutable {@link ActionList}. */
    public static ActionList parse(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        List<Action> parsed = new ArrayList<>(lines.size());
        for (String line : lines) {
            parsed.add(ActionParser.parse(line).action());
        }
        return new ActionList(parsed);
    }

    /** An action list wrapping already-built actions (for example from a programmatic source). */
    public static ActionList of(List<Action> actions) {
        Objects.requireNonNull(actions, "actions");
        return new ActionList(actions);
    }

    /** The actions in run order. */
    public List<Action> actions() {
        return actions;
    }

    /**
     * Whether <em>any</em> action in this list is flagged to run off the main thread. A {@code true} result
     * does <b>not</b> mean the whole list may run async: a sync action in a mixed list must still run on the
     * main/region thread. Use {@link #syncActions()}/{@link #asyncActions()} to route per lane.
     */
    public boolean hasAsyncActions() {
        return actions.stream().anyMatch(Action::async);
    }

    /** The sync (main/region-thread) actions, in declaration order — never safe to hop off-thread. */
    public List<Action> syncActions() {
        return actions.stream().filter(action -> !action.async()).toList();
    }

    /** The async-flagged actions, in declaration order — a driver may run these off the main thread. */
    public List<Action> asyncActions() {
        return actions.stream().filter(Action::async).toList();
    }

    /** Run every action in declaration order against the context. */
    public void run(ActionContext context) {
        Objects.requireNonNull(context, "context");
        for (Action action : actions) {
            action.run(context);
        }
    }
}

package com.uxplima.uxmlib.menu.property;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.NullMarked;

/**
 * The capability a property uses to open a yes/no confirm as a menu-engine child window rather than a uxmLib
 * {@code ConfirmMenu}. Defined here, in the property package, so a property depends only on this small port and not
 * on the engine that implements it; the engine supplies the implementation, mirroring {@link SelectorOpener} exactly.
 *
 * <p>An opener receives the viewer, a resolved title, and the two decisions — {@code onYes} run once if the viewer
 * confirms, {@code onNo} run once if they decline — and shows them as a child window the one menu listener routes and
 * the one teardown owns. A {@link ListProperty}'s remove gesture uses it to gate a deletion, and it gates on the
 * one holder, listener and teardown every other window uses rather than standing up a second of each.
 *
 * <p>A property only ever opens an engine confirm when its {@link ClickContext} carries an opener (the engine editor
 * runtime threads one in); a context with no confirm opener — the legacy {@code EntityEditorView} path — leaves the
 * property on its uxmLib {@code ConfirmMenu} fallback, so both runtimes coexist while editors migrate.
 */
@NullMarked
public interface ConfirmOpener {

    /**
     * Open a two-button confirm child window for {@code viewer}: confirming runs {@code onYes} once, declining runs
     * {@code onNo} once. The {@code title} is already resolved from a message key, so the opener adds no inline
     * user-facing text of its own.
     */
    void openConfirm(Player viewer, Component title, Runnable onYes, Runnable onNo);
}

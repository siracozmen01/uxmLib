package com.uxplima.uxmlib.gui;

import org.bukkit.entity.Player;

/**
 * A built inventory interface a viewer can open. The framework (rows, slots, click routing, pagination)
 * lands with the gui module's first feature pass; this contract fixes the open seam.
 */
public interface Gui {

    /** Open this interface for {@code viewer} on its owning region thread. */
    void open(Player viewer);
}

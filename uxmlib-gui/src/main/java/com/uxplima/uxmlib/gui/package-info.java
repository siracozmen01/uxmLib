/**
 * Inventory-menu framework. A {@link com.uxplima.uxmlib.gui.Gui} is a chest menu that holds its own
 * inventory, so the one registered {@link com.uxplima.uxmlib.gui.GuiListener} routes each click back to
 * the owning menu. Clicks are cancelled by default (an unconfigured menu cannot leak items), and the
 * clicked slot's {@link com.uxplima.uxmlib.gui.item.GuiAction} runs. Build menus with
 * {@link com.uxplima.uxmlib.gui.Guis}: {@link com.uxplima.uxmlib.gui.SimpleGui} for one page,
 * {@link com.uxplima.uxmlib.gui.PaginatedGui} for many. Slot contents: icons, click handlers, and
 * per-render rebuilds, live in {@link com.uxplima.uxmlib.gui.item}; building a whole menu from a config
 * file is the menu engine's work and lives in {@code uxmlib-menu}. Anvil text capture lives in
 * {@link com.uxplima.uxmlib.gui.anvil}.
 */
@NullMarked
package com.uxplima.uxmlib.gui;

import org.jspecify.annotations.NullMarked;

package com.uxplima.uxmlib.gui;

import org.bukkit.inventory.ItemStack;

/**
 * A per-viewer transform applied to an item's icon during render: given the viewing player's
 * {@link RenderContext} and the icon resolved so far, it returns the icon to actually show. Chain several
 * with {@link DisplayModifiers#of} to build a pipeline — set the viewer's own head, resolve placeholders,
 * translate text for their locale — and attach them to an item with
 * {@link DisplayModifiers#apply(GuiItem, java.util.List)}.
 */
@FunctionalInterface
public interface DisplayModifier {

    /** Transform {@code base} for this viewer, returning the icon to display. */
    ItemStack modify(RenderContext context, ItemStack base);
}

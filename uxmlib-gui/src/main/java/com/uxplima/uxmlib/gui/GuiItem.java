package com.uxplima.uxmlib.gui;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * An icon in a {@link Gui}: the {@link ItemStack} shown plus the {@link GuiAction} run when it is
 * clicked. Pairing them at construction decouples an item from where it is placed, so the same item can
 * be reused in different slots or pages.
 */
public record GuiItem(ItemStack item, GuiAction action) {

    public GuiItem {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(action, "action");
    }

    /** A clickable button: the icon plus a handler run on click. */
    public static GuiItem button(ItemStack item, Consumer<InventoryClickEvent> onClick) {
        return new GuiItem(item, new GuiAction.Run(onClick));
    }

    /** A display-only icon with no click behaviour. */
    public static GuiItem display(ItemStack item) {
        return new GuiItem(item, GuiAction.None.INSTANCE);
    }
}

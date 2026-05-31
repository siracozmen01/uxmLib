package com.uxplima.uxmlib.item;

import org.bukkit.inventory.ItemStack;

/**
 * Fluent builder for an {@link ItemStack}. The concrete implementation lands with the item module's
 * first feature pass; this contract fixes the shape consumers code against.
 */
public interface ItemBuilder {

    /** Materialise the configured stack. Each call returns an independent copy. */
    ItemStack build();
}

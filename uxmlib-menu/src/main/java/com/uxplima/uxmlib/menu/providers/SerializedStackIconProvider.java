package com.uxplima.uxmlib.menu.providers;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.item.SerializedItems;
import com.uxplima.uxmlib.menu.runtime.MenuContext;

/**
 * Turns a {@code b64:<base64>} material spec into the fully-deserialized {@link ItemStack} it encodes: every component,
 * enchantment, name, and lore the operator captured. It reads the token through {@link SerializedItems}, the one place
 * that knows what a written-down item looks like, so this provider and whatever wrote the token cannot drift apart.
 *
 * <p>The stack is returned as the base icon, so the item's name, lore, and decor layer on top of it exactly as they do
 * over a material or a skull. With {@code lore-mode = append} the stack's own lore is preserved and the spec lore is
 * added beneath it.
 *
 * <p>A token that will not read is caught here rather than left to travel. {@link SerializedItems#decode} raises it,
 * because from its position damage and a plain material name must not look alike; from this position they may, because
 * a menu being drawn has to finish being drawn. So the icon falls through to the renderer's material fallback, and the
 * window opens missing one tile instead of not opening at all.
 *
 * <p>The provider claims only the {@code b64:} prefix, never a bare material name.
 */
final class SerializedStackIconProvider implements IconProvider {

    @Override
    public Optional<ItemStack> icon(String spec, MenuContext ctx) {
        String trimmed = spec.trim();
        if (!SerializedItems.isSerialized(trimmed)) {
            return Optional.empty();
        }
        try {
            return SerializedItems.decode(trimmed);
        } catch (IllegalArgumentException damaged) {
            return Optional.empty();
        }
    }
}

package com.uxplima.uxmlib.gui;

import java.util.Map;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import org.jspecify.annotations.Nullable;

/**
 * Writes {@link GuiItem}s into a backing {@link Inventory}, resolving each item against a viewer through
 * a {@link RenderContext}. A {@link GuiItem.Static} resolves the same for everyone and can be written
 * with no viewer; a dynamic, stateful, or animated item needs a viewer, so when none is present (the
 * inventory has not been opened yet) those slots are left for the next render once a viewer exists.
 *
 * <p>Kept separate from {@code AbstractGui} so the menu class stays small and rendering has one home.
 */
final class GuiRender {

    private GuiRender() {}

    /** The first player viewing {@code inventory}, or {@code null} if none (or only non-players). */
    static @Nullable Player firstViewer(@Nullable Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        for (HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** Write one slot, using the inventory's current viewer to resolve a non-static item. */
    static void writeSlot(Inventory inventory, Gui gui, int slot, GuiItem item) {
        writeSlot(inventory, gui, slot, item, firstViewer(inventory));
    }

    /** Write one slot for {@code viewer}; a static item is written even when {@code viewer} is null. */
    static void writeSlot(Inventory inventory, Gui gui, int slot, GuiItem item, @Nullable Player viewer) {
        if (item instanceof GuiItem.Static fixed) {
            inventory.setItem(slot, fixed.item());
        } else if (viewer != null) {
            setIfChanged(inventory, slot, item.icon(new RenderContext(viewer, gui, slot)));
        }
    }

    /** Write every item in {@code items} for {@code viewer} (resolving statics regardless). */
    static void renderAll(Inventory inventory, Gui gui, Map<Integer, GuiItem> items, @Nullable Player viewer) {
        for (Map.Entry<Integer, GuiItem> entry : items.entrySet()) {
            writeSlot(inventory, gui, entry.getKey(), entry.getValue(), viewer);
        }
    }

    /**
     * Re-render only the items that can change on a tick — dynamic, stateful, animated — for {@code viewer},
     * leaving static slots alone. The per-tick path so a 60-slot menu of mostly static buttons doesn't
     * rewrite every slot 20×/sec, and only resolves the suppliers whose result can differ.
     */
    static void renderDynamic(Inventory inventory, Gui gui, Map<Integer, GuiItem> items, Player viewer) {
        for (Map.Entry<Integer, GuiItem> entry : items.entrySet()) {
            if (!(entry.getValue() instanceof GuiItem.Static)) {
                int slot = entry.getKey();
                setIfChanged(inventory, slot, entry.getValue().icon(new RenderContext(viewer, gui, slot)));
            }
        }
    }

    /** Set {@code slot} only when {@code next} differs from what is there, to avoid needless updates. */
    private static void setIfChanged(Inventory inventory, int slot, org.bukkit.inventory.ItemStack next) {
        if (!next.equals(inventory.getItem(slot))) {
            inventory.setItem(slot, next);
        }
    }

    /** Reopen {@code fresh} (a rebuilt inventory) for everyone who was viewing {@code old}. */
    static void reopen(Inventory old, Inventory fresh) {
        for (HumanEntity viewer : new java.util.ArrayList<>(old.getViewers())) {
            viewer.openInventory(fresh);
        }
    }

    /** Close {@code inventory} for every current viewer. */
    static void closeAll(@Nullable Inventory inventory) {
        if (inventory != null) {
            for (HumanEntity viewer : new java.util.ArrayList<>(inventory.getViewers())) {
                viewer.closeInventory();
            }
        }
    }

    /** Close {@code inventory} for {@code viewer}, if they are viewing it. */
    static void close(@Nullable Inventory inventory, HumanEntity viewer) {
        if (inventory != null && inventory.getViewers().contains(viewer)) {
            viewer.closeInventory();
        }
    }
}

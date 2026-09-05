package com.uxplima.uxmlib.menu.render;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.menu.EntityListSpec;
import com.uxplima.uxmlib.menu.eval.Pagination;
import com.uxplima.uxmlib.menu.runtime.ListViewState;
import org.jspecify.annotations.NullMarked;

/**
 * Lays an {@link EntityListSpec} into an open inventory for one viewer, the list counterpart of {@link EditorRenderer}.
 * It paints the spec's filler everywhere, then one entity icon per content slot for the requested page (sliced through
 * the shared {@link Pagination} so a page flip never re-queries), then the previous/next nav buttons, then the optional
 * create and action buttons: recording each clickable slot onto the holder's {@link ListViewState} so the one listener
 * can route a later click back to the entity or button drawn there. The entity snapshot is re-read fresh from the spec
 * on every call (a page flip is just a repaint of the next slice), and the per-entity icon is the caller's already-
 * prepared {@code ItemStack}, so this renderer makes no presentation decision of its own beyond the filler and the
 * nav/create/action button names the spec resolved.
 *
 * <p>A page is the spec's content slots holding this page's entities, its nav, create and action slots holding their
 * icons, and the filler everywhere else. Every page of every list is drawn by this one method, so two pages of the same
 * list cannot drift apart.
 */
@NullMarked
public final class ListViewRenderer {

    public ListViewRenderer() {}

    /**
     * Fill {@code inv} with the {@code spec}'s page {@code page} for {@code viewer}, recording every clickable slot
     * onto {@code state}. The caller clears the list's slot routing first, so this never needs to; it just records
     * the slots it paints and returns the clamped page index so the holder can stamp it back.
     */
    public int populate(Inventory inv, EntityListSpec spec, ListViewState state, Player viewer, int page) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(viewer, "viewer");
        fill(inv, spec.filler());
        int clamped = paintEntities(inv, spec, state, viewer, page);
        paintNav(inv, spec, state);
        paintCreate(inv, spec, state, viewer);
        paintAction(inv, spec, state, viewer);
        paintExtras(inv, spec, state, viewer);
        return clamped;
    }

    /** Slice the snapshot for {@code page}, draw each entity's icon at its content slot, record each as an entity. */
    private int paintEntities(Inventory inv, EntityListSpec spec, ListViewState state, Player viewer, int page) {
        Pagination.Page<Object> sliced = Pagination.paginate(spec.entities(), spec.contentSlots(), page);
        for (Map.Entry<Integer, Object> placed : sliced.placements()) {
            int slot = placed.getKey();
            Object entity = placed.getValue();
            inv.setItem(slot, spec.iconRenderer().apply(viewer, entity));
            state.recordEntity(slot, entity);
        }
        return sliced.page();
    }

    /** Draw the previous/next nav buttons at their slots and record both so a click re-paginates the holder. */
    private void paintNav(Inventory inv, EntityListSpec spec, ListViewState state) {
        inv.setItem(spec.prevSlot(), navButton(spec, spec.prevName()));
        inv.setItem(spec.nextSlot(), navButton(spec, spec.nextName()));
        state.recordNav(spec.prevSlot(), spec.nextSlot());
    }

    private ItemStack navButton(EntityListSpec spec, Component name) {
        return ItemBuilder.of(spec.navIcon()).name(name).build();
    }

    /** Paint the optional create button when the spec carries one, recording its click as a plain button. */
    private void paintCreate(Inventory inv, EntityListSpec spec, ListViewState state, Player viewer) {
        if (spec.createSlot().isEmpty()
                || spec.createName().isEmpty()
                || spec.onCreate().isEmpty()) {
            return;
        }
        int slot = spec.createSlot().getAsInt();
        inv.setItem(
                slot,
                ItemBuilder.of(spec.createIcon())
                        .name(spec.createName().orElseThrow())
                        .build());
        state.recordButton(slot, () -> spec.onCreate().orElseThrow().accept(viewer));
    }

    /** Paint the optional action button when the spec carries one, recording its click as a plain button. */
    private void paintAction(Inventory inv, EntityListSpec spec, ListViewState state, Player viewer) {
        if (spec.actionSlot().isEmpty()
                || spec.actionName().isEmpty()
                || spec.onAction().isEmpty()) {
            return;
        }
        int slot = spec.actionSlot().getAsInt();
        inv.setItem(
                slot,
                ItemBuilder.of(spec.actionIcon())
                        .name(spec.actionName().orElseThrow())
                        .build());
        state.recordButton(slot, () -> spec.onAction().orElseThrow().accept(viewer));
    }

    /** Paint each of the spec's fixed extra buttons as-is, recording each click as a plain button on the state. */
    private void paintExtras(Inventory inv, EntityListSpec spec, ListViewState state, Player viewer) {
        for (EntityListSpec.ExtraButton button : spec.extraButtons()) {
            inv.setItem(button.slot(), button.icon());
            state.recordButton(button.slot(), () -> button.onClick().accept(viewer));
        }
    }

    /** Fill every slot with the spec's filler so no vanilla slot shows through behind the icons and buttons. */
    private void fill(Inventory inv, Material filler) {
        ItemStack item = ItemBuilder.of(filler).name(Component.empty()).build();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, item);
        }
    }
}

package com.uxplima.uxmlib.menu.render;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.style.Tiles;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.menu.EditorSpec;
import com.uxplima.uxmlib.menu.EntityEditorLayout;
import com.uxplima.uxmlib.menu.SlotFit;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.runtime.EditorState;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.NullMarked;

/**
 * Lays an {@link EditorSpec} into an open inventory for one viewer, the editor counterpart of {@link MenuRenderer}. It
 * paints the layout's filler everywhere, then one button per {@link EditableProperty} at the layout's ordered property
 * slots, then the back button, then the optional delete button: recording each clickable slot onto the holder's {@link
 * EditorState} so the one listener can route a later click back to the property or button drawn there. The property
 * list is re-read fresh from the subject on every call (a page-less re-render of an editor is just a repaint), so a
 * value changed by a click shows on the next draw.
 *
 * <p>A per-property button is the property's own icon, its {@code label()} catalog name, and the value-lore catalog
 * line wrapping {@code valueLore(viewer)}, which is what makes two editors built from different specs identical window.
 */
@NullMarked
public final class EditorRenderer {

    private final GuiText guiText;

    /** Asked for on every draw, never held: a theme is a file an operator edits while the server runs. */
    private final Supplier<Theme> theme;

    public EditorRenderer(GuiText guiText, Supplier<Theme> theme) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    /**
     * Fill {@code inv} with the editor {@code spec} resolves to for {@code viewer} editing {@code state}'s subject,
     * recording every clickable slot onto {@code state}. The caller clears the editor's slot routing first, so this
     * never needs to; it just records the slots it paints.
     */
    public void populate(Inventory inv, EditorSpec spec, EditorState state, Player viewer) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(viewer, "viewer");
        EntityEditorLayout layout = spec.layout();
        fill(inv, layout);
        paintProperties(inv, spec, state, viewer);
        paintBack(inv, spec, state, viewer);
        paintDelete(inv, spec, state, viewer);
    }

    /** Paint one button per property at the layout's ordered slots, recording each as a property slot. */
    private void paintProperties(Inventory inv, EditorSpec spec, EditorState state, Player viewer) {
        List<EditableProperty> props = spec.propertiesFor(state.subject());
        List<Integer> slots = spec.layout().propertySlots();
        int drawn = SlotFit.fit(props.size(), slots.size(), "editor properties", spec.layout());
        for (int i = 0; i < drawn; i++) {
            EditableProperty property = props.get(i);
            int slot = slots.get(i);
            inv.setItem(slot, propertyButton(viewer, spec, property));
            state.recordProperty(slot, property);
        }
    }

    /**
     * One property's button: the property icon, the shared value-lore, and the property's own label as the title
     * on the first lore line, which is where the canon keeps a tile's title. The label used to be the display
     * name, which put it outside the tooltip's body and left the lore opening on the generic word every property
     * shares ("setting") rather than on the setting the player is looking at.
     */
    private ItemStack propertyButton(Player viewer, EditorSpec spec, EditableProperty property) {
        List<Component> lore =
                List.of(guiText.text(viewer, spec.valueLore(), Map.of("value", property.valueLore(viewer))));
        return ItemBuilder.of(property.icon())
                .name(Tiles.blankName())
                .lore(Tiles.titled(theme.get(), guiText.text(viewer, property.label()), lore))
                .build();
    }

    /** Paint the back button and record it as a plain-button slot whose click runs the spec's back callback. */
    private void paintBack(Inventory inv, EditorSpec spec, EditorState state, Player viewer) {
        EntityEditorLayout layout = spec.layout();
        ItemStack back = ItemBuilder.of(layout.backIcon())
                .name(guiText.text(viewer, spec.backName()))
                .build();
        inv.setItem(layout.backSlot(), back);
        state.recordButton(layout.backSlot(), () -> spec.onBack().accept(viewer));
    }

    /**
     * Paint the optional delete button when the spec carries one, recording its click as a plain button. The click
     * runs the spec's delete handler directly; gating it behind a confirm menu is the confirm increment's seam, so
     * an editor opened before that increment lands carries no consumer that relies on the gate.
     */
    private void paintDelete(Inventory inv, EditorSpec spec, EditorState state, Player viewer) {
        if (!spec.hasDelete()) {
            return;
        }
        EntityEditorLayout layout = spec.layout();
        ItemStack delete = ItemBuilder.of(layout.deleteIcon())
                .name(guiText.text(viewer, spec.deleteName().orElseThrow()))
                .build();
        int slot = layout.deleteSlot().getAsInt();
        inv.setItem(slot, delete);
        state.recordButton(slot, () -> spec.onDelete().orElseThrow().accept(viewer, state.subject()));
    }

    /** Fill every slot with the layout's filler so no vanilla slot shows through behind the buttons. */
    private void fill(Inventory inv, EntityEditorLayout layout) {
        ItemStack filler =
                ItemBuilder.of(layout.filler()).name(Component.empty()).build();
        for (int slot = 0; slot < layout.rows() * 9; slot++) {
            inv.setItem(slot, filler);
        }
    }
}

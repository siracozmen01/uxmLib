package com.uxplima.uxmlib.menu.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.gui.style.Tiles;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a sub-menu for editing a list of string entries: add, remove behind a confirm,
 * reorder, and edit each line. It is backed by a single {@code List<String>} the caller reads and writes through a use case (e.g. a
 * hologram's text lines). The sub-menu draws one button per entry into the configured entry slots: left-click moves the
 * line up, right-click moves it down, shift-left-click edits the line through an anvil, and shift-right-click removes
 * it (confirm-gated). An add button opens an anvil for a new line. Each mutation rewrites the whole list through the
 * setter off the tick thread via the shared {@link Scheduler}, then re-opens the sub-menu so the change shows.
 *
 * <p>Every label, hint, and the sub-menu title are catalog keys resolved through {@link GuiText}; the slots and
 * materials come from the caller (the editor layout conf), so nothing is hardcoded. The setter is the module's existing
 * application use case wrapped as a {@link Consumer}; this property holds no domain logic.
 *
 * <p>The sub-menu opens as an engine child window the one menu listener routes (its entry/add/back buttons are {@link
 * SelectorButton}s and a removal gates through the click's {@link ConfirmOpener} confirm child) and each mutation
 * reopens the engine list, so the whole flow stays on a single holder and teardown.
 */
@NullMarked
public final class ListProperty implements EditableProperty {

    private final String inputKey;
    private final String label;
    private final Material icon;
    private final GuiText guiText;

    /** Asked for on every draw, never held: a theme is a file an operator edits while the server runs. */
    private final Supplier<Theme> theme;

    private final Supplier<List<String>> current;
    private final Consumer<List<String>> setter;
    private final ListPropertyText keys;
    private final ListPropertyLayout layout;
    private final TextInput textInput;
    private final Scheduler scheduler;

    public ListProperty(
            String inputKey,
            String label,
            Material icon,
            GuiText guiText,
            Supplier<Theme> theme,
            Supplier<List<String>> current,
            Consumer<List<String>> setter,
            ListPropertyText keys,
            ListPropertyLayout layout,
            TextInput textInput,
            Scheduler scheduler) {
        this.inputKey = Objects.requireNonNull(inputKey, "inputKey");
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.current = Objects.requireNonNull(current, "current");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return Integer.toString(current.get().size());
    }

    @Override
    public void onClick(PropertyClick click) {
        Objects.requireNonNull(click, "click");
        scheduler.entity(click.viewer(), () -> open(click));
    }

    /**
     * Open the list sub-menu as an engine child window: the per-entry/add/back buttons are handed to the engine opener
     * as {@link SelectorButton}s so the one menu listener routes them. The entry button is gesture-aware (left/right
     * move, shift-left edit, shift-right remove); the add and back buttons ignore the gesture. After any mutation the
     * list reopens itself through this same method, so a change shows; back reopens the parent editor via the click.
     */
    private void open(PropertyClick click) {
        List<String> entries = current.get();
        List<Integer> slots = layout.entrySlots();
        List<SelectorButton> buttons = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < slots.size(); i++) {
            buttons.add(engineEntryButton(click, entries.get(i), i, slots.get(i)));
        }
        buttons.add(SelectorButton.of(layout.addSlot(), addIcon(click), () -> add(click)));
        buttons.add(SelectorButton.of(
                layout.backSlot(), backIcon(click), () -> click.reopen().run()));
        click.opener()
                .openSelector(
                        click.viewer(),
                        guiText.text(click.viewer(), keys.title()),
                        layout.rows(),
                        layout.fillerIcon(),
                        buttons);
    }

    /** A gesture-aware engine entry button: shift-left edits, shift-right removes, left moves up, right moves down. */
    private SelectorButton engineEntryButton(PropertyClick click, String entry, int index, int slot) {
        ItemStack icon = entryIcon(click, entry);
        ChildClickHandler handler = (rightClick, shiftClick) -> {
            if (shiftClick && !rightClick) {
                edit(click, index, entry);
            } else if (shiftClick) {
                confirmRemove(click, index);
            } else if (rightClick) {
                move(click, index, 1);
            } else {
                move(click, index, -1);
            }
        };
        return new SelectorButton(slot, icon, handler);
    }

    private ItemStack entryIcon(PropertyClick click, String entry) {
        return ItemBuilder.of(layout.entryIcon())
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        theme.get(),
                        guiText.text(click.viewer(), keys.entryName(), Map.of("entry", entry)),
                        guiText.text(click.viewer(), keys.entryHints())))
                .build();
    }

    private ItemStack addIcon(PropertyClick click) {
        return ItemBuilder.of(layout.addIcon())
                .name(guiText.text(click.viewer(), keys.addName()))
                .build();
    }

    private ItemStack backIcon(PropertyClick click) {
        return ItemBuilder.of(layout.backIcon())
                .name(guiText.text(click.viewer(), keys.backName()))
                .build();
    }

    private void add(PropertyClick click) {
        textInput.prompt(
                click.viewer(),
                InputRequest.of(inputKey, keys.addPrompt()),
                text -> applyAdd(click, text),
                () -> open(click));
    }

    /** Append a non-blank submitted line; a blank line reopens the list unchanged. Package-private for unit tests. */
    void applyAdd(PropertyClick click, String text) {
        if (text.isBlank()) {
            open(click);
            return;
        }
        List<String> next = new ArrayList<>(current.get());
        next.add(text);
        save(click, next);
    }

    private void edit(PropertyClick click, int index, String existing) {
        textInput.prompt(
                click.viewer(),
                InputRequest.of(inputKey, keys.editPrompt(), Map.of("entry", existing)),
                text -> applyEdit(click, index, text),
                () -> open(click));
    }

    /** Replace entry {@code index} with a non-blank submitted line; otherwise reopen unchanged. Package-private for tests. */
    void applyEdit(PropertyClick click, int index, String text) {
        List<String> next = new ArrayList<>(current.get());
        if (!text.isBlank() && index < next.size()) {
            next.set(index, text);
            save(click, next);
            return;
        }
        open(click);
    }

    /** Gate a removal behind an engine confirm child: confirming removes the entry and reopens the list, declining reopens. */
    private void confirmRemove(PropertyClick click, int index) {
        click.confirmOpener()
                .openConfirm(
                        click.viewer(),
                        guiText.text(click.viewer(), keys.removeConfirm()),
                        () -> remove(click, index),
                        () -> reopen(click));
    }

    private void remove(PropertyClick click, int index) {
        List<String> next = new ArrayList<>(current.get());
        if (index >= 0 && index < next.size()) {
            next.remove(index);
            save(click, next);
        } else {
            reopen(click);
        }
    }

    private void move(PropertyClick click, int index, int direction) {
        List<String> next = new ArrayList<>(current.get());
        int target = index + direction;
        if (index >= 0 && index < next.size() && target >= 0 && target < next.size()) {
            String moved = next.remove(index);
            next.add(target, moved);
            save(click, next);
        } else {
            reopen(click);
        }
    }

    private void save(PropertyClick click, List<String> next) {
        scheduler.async(() -> {
            setter.accept(List.copyOf(next));
            reopen(click);
        });
    }

    /** Reopen the list sub-menu on the viewer's entity thread. */
    private void reopen(PropertyClick click) {
        scheduler.entity(click.viewer(), () -> open(click));
    }
}

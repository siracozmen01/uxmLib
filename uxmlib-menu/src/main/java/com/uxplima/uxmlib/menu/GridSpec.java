package com.uxplima.uxmlib.menu;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import org.jspecify.annotations.NullMarked;

/**
 * The recipe the engine draws a slot-grid canvas from: the grid analog of an {@link EntityListSpec} or {@link
 * EditorSpec}, opened through {@link Menus#openGrid}. It describes a canvas that mirrors an edited menu's slots: the
 * engine sizes a window one row taller than the menu (capped at six), paints the content rows with the caller's items
 * and reserves the last row for controls, paginating a six-row menu across two pages so the control row never collides
 * with content.
 *
 * <p>Only the layout lives here; the editing behaviour is the {@link GridHandlers} handed alongside. The content is a
 * {@link Supplier} of {@code menuSlot -> MenuItemSpec}, re-read on every draw (the same discipline {@link
 * EntityListSpec} uses for its entities), so a caller mutating its edit model and calling {@link GridView#reRender}
 * shows the change without rebuilding the spec: and the engine renders each preview through its own {@code
 * ItemRenderer}, so the consumer never touches a renderer. The empty / blocker / nav / control icons are {@link
 * ItemStack}s the caller already built from the caller's catalog, so a grid opened through this spec carries no inline
 * user-facing literal of its own.
 */
@NullMarked
public record GridSpec(
        Component title,
        int menuRows,
        Supplier<Map<Integer, MenuItemSpec>> content,
        ItemStack emptyIcon,
        ItemStack blockerIcon,
        ItemStack prevIcon,
        ItemStack nextIcon,
        List<Control> controls) {

    public GridSpec {
        Objects.requireNonNull(title, "title");
        if (menuRows < 1 || menuRows > 6) {
            throw new IllegalArgumentException("menuRows must be 1..6, was " + menuRows);
        }
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(emptyIcon, "emptyIcon");
        Objects.requireNonNull(blockerIcon, "blockerIcon");
        Objects.requireNonNull(prevIcon, "prevIcon");
        Objects.requireNonNull(nextIcon, "nextIcon");
        controls = List.copyOf(Objects.requireNonNull(controls, "controls"));
    }

    /**
     * The first menu slot in {@code [0, menuRows*9)} that no content item occupies on this draw, or empty when the
     * canvas is full: where the engine appends an item shift-clicked out of the operator's inventory. The content
     * supplier is re-read here, so appending one item then re-rendering makes the next append land on the following
     * free slot. It scans only the chest slots, never the bottom-inventory range, so a shift-click always fills the
     * grid itself.
     */
    public OptionalInt firstEmptySlot() {
        Map<Integer, MenuItemSpec> filled = content.get();
        int capacity = menuRows * 9;
        for (int slot = 0; slot < capacity; slot++) {
            if (!filled.containsKey(slot)) {
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * One button on the grid's bottom control row: the column {@code 1..7} it sits at within that row (the engine adds
     * the row's base slot so the caller stays window-height agnostic), the already-built icon to place there, and the
     * handler run with the live viewer when it is clicked.
     *
     * <p>Columns {@code 0} and {@code 8} are the engine's previous / next pagination buttons and are refused here
     * rather than left to the caller's care. A control there is painted after the nav button, so it covers the nav
     * icon, while the click router asks about a page flip first: the viewer would see this button and get a page
     * turn. That only happens on a canvas tall enough to paginate, which is the one a caller is least likely to open
     * while testing, so the guard is uniform rather than page-count aware.
     *
     * @param column the control-row column {@code 1..7} the button is drawn in
     * @param icon the prepared icon to place (name/lore already applied by the caller)
     * @param onClick invoked with the live viewer when the button is clicked
     */
    public record Control(int column, ItemStack icon, Consumer<Player> onClick) {

        public Control {
            if (column < 1 || column > 7) {
                throw new IllegalArgumentException("column must be 1..7 (0 and 8 are the page buttons), was " + column);
            }
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(onClick, "onClick");
        }
    }
}

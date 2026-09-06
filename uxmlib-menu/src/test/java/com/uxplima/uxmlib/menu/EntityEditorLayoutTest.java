package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.bukkit.Material;

import org.junit.jupiter.api.Test;

/**
 * The layout an operator writes for an entity editor, and the bounds it is held to where it is built. Every value
 * here is a slot number in a file, so the failure this record exists to prevent is a window that opens with a button
 * outside it: an operator who typed 40 into a three-row editor gets a named error at load rather than a button that
 * silently never appears.
 */
class EntityEditorLayoutTest {

    @Test
    void aLayoutKeepsTheSlotsInThePropertyOrderItWasGiven() {
        EntityEditorLayout layout = EntityEditorLayout.codeDefault(List.of(12, 10, 14), 22);

        assertThat(layout.propertySlots())
                .as("the i-th property is drawn into the i-th slot, so the order is the layout")
                .containsExactly(12, 10, 14);
    }

    @Test
    void theCodeDefaultIsThreeRowsWithABackButtonAndNoDelete() {
        EntityEditorLayout layout = EntityEditorLayout.codeDefault(List.of(11), 22);

        assertThat(layout.rows()).isEqualTo(3);
        assertThat(layout.backSlot()).isEqualTo(22);
        assertThat(layout.deleteSlot())
                .as("an editor for a non-deletable entity shows no delete button")
                .isEmpty();
        assertThat(layout.backIcon()).isEqualTo(Material.ARROW);
        assertThat(layout.filler()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void theDeletingDefaultCarriesTheDeleteSlotItWasGiven() {
        EntityEditorLayout layout = EntityEditorLayout.withDelete(List.of(11), 22, 26);

        assertThat(layout.deleteSlot()).hasValue(26);
        assertThat(layout.deleteIcon()).isEqualTo(Material.BARRIER);
        assertThat(layout.backSlot()).as("the two buttons are not each other").isEqualTo(22);
    }

    @Test
    void aRowCountOutsideTheChestIsRefused() {
        assertThatThrownBy(() -> layout(0, List.of(1), 2, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows");
        assertThatThrownBy(() -> layout(7, List.of(1), 2, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rows");
        assertThatCode(() -> layout(1, List.of(1), 2, OptionalInt.empty())).doesNotThrowAnyException();
        assertThatCode(() -> layout(6, List.of(1), 2, OptionalInt.empty())).doesNotThrowAnyException();
    }

    @Test
    void anEditorWithNowhereToDrawItsPropertiesIsRefused() {
        assertThatThrownBy(() -> layout(3, List.of(), 22, OptionalInt.empty()))
                .as("an editor with no property slots is a window that edits nothing")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("propertySlots");
    }

    @Test
    void aPropertySlotOutsideTheWindowIsNamedRatherThanDrawnNowhere() {
        assertThatThrownBy(() -> layout(3, List.of(27), 22, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property slot");
        assertThatThrownBy(() -> layout(3, List.of(-1), 22, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("property slot");
        assertThatCode(() -> layout(3, List.of(26), 22, OptionalInt.empty()))
                .as("the last slot of the last row is inside the window")
                .doesNotThrowAnyException();
    }

    @Test
    void aBackSlotOutsideTheWindowIsNamed() {
        assertThatThrownBy(() -> layout(3, List.of(11), 27, OptionalInt.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backSlot");
    }

    @Test
    void aDeleteSlotOutsideTheWindowIsNamed() {
        assertThatThrownBy(() -> layout(3, List.of(11), 22, OptionalInt.of(27)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deleteSlot");
    }

    @Test
    void anAbsentDeleteSlotIsNotABoundsFailure() {
        assertThatCode(() -> layout(1, List.of(1), 2, OptionalInt.empty()))
                .as("a one-row editor with no delete button has no slot to be out of range")
                .doesNotThrowAnyException();
    }

    @Test
    void theSlotsAreCopiedSoALaterEditOfTheCallersListDoesNotMoveTheButtons() {
        List<Integer> slots = new ArrayList<>(List.of(11));
        EntityEditorLayout layout = EntityEditorLayout.codeDefault(slots, 22);

        slots.add(12);

        assertThat(layout.propertySlots()).containsExactly(11);
        assertThatThrownBy(() -> layout.propertySlots().add(13)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static EntityEditorLayout layout(int rows, List<Integer> propertySlots, int backSlot, OptionalInt delete) {
        return new EntityEditorLayout(
                rows,
                propertySlots,
                backSlot,
                delete,
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }
}

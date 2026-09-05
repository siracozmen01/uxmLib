package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The canvas the menu editor draws an edited menu onto. Two things here are behaviour rather than description: the
 * row guard, and where a shift-clicked item lands.
 */
class GridSpecTest {

    private MenuItemSpec item;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MenuSpec spec =
                new MenuSpecLoader().parse("rows = 1\nitems { one { slot = 0, material = STONE, name = \"n\" } }");
        item = Objects.requireNonNull(spec.items().get("one"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack icon() {
        return new ItemStack(Material.PAPER);
    }

    private static GridSpec grid(int menuRows, Map<Integer, MenuItemSpec> content) {
        return new GridSpec(
                Component.text("Editing"), menuRows, () -> content, icon(), icon(), icon(), icon(), List.of());
    }

    // -- the window the canvas has to fit ---------------------------------------------------------------------

    @Test
    void theRowCountsAnEditedMenuCanHaveAreAccepted() {
        assertThat(grid(1, Map.of()).menuRows()).isEqualTo(1);
        assertThat(grid(6, Map.of()).menuRows()).isEqualTo(6);
    }

    @Test
    void aRowCountNoMenuHasIsRefusedAndSaysWhatItWas() {
        assertThatIllegalArgumentException().isThrownBy(() -> grid(0, Map.of())).withMessageContaining("was 0");
        assertThatIllegalArgumentException().isThrownBy(() -> grid(7, Map.of())).withMessageContaining("was 7");
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert each compact-constructor guard fires by name
    void everyCollaboratorIsRefusedByNameWhenItIsMissing() {
        assertThatNullPointerException()
                .isThrownBy(() -> new GridSpec(null, 3, Map::of, icon(), icon(), icon(), icon(), List.of()))
                .withMessageContaining("title");
        assertThatNullPointerException()
                .isThrownBy(() -> new GridSpec(Component.text("t"), 3, null, icon(), icon(), icon(), icon(), List.of()))
                .withMessageContaining("content");
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new GridSpec(Component.text("t"), 3, Map::of, null, icon(), icon(), icon(), List.of()))
                .withMessageContaining("emptyIcon");
        assertThatNullPointerException()
                .isThrownBy(() -> new GridSpec(Component.text("t"), 3, Map::of, icon(), icon(), icon(), icon(), null))
                .withMessageContaining("controls");
    }

    // -- the controls -----------------------------------------------------------------------------------------

    @Test
    void theControlsAreCopiedSoTheCallerCannotChangeThemAfterwards() {
        List<GridSpec.Control> controls = new ArrayList<>();
        controls.add(new GridSpec.Control(3, icon(), viewer -> {}));
        GridSpec spec = new GridSpec(Component.text("t"), 3, Map::of, icon(), icon(), icon(), icon(), controls);

        controls.clear();

        assertThat(spec.controls()).hasSize(1);
        assertThatThrownBy(() -> spec.controls().add(new GridSpec.Control(4, icon(), viewer -> {})))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aControlColumnOutsideTheRowIsRefusedAndSaysWhatItWas() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GridSpec.Control(-1, icon(), viewer -> {}))
                .withMessageContaining("was -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GridSpec.Control(9, icon(), viewer -> {}))
                .withMessageContaining("was 9");
    }

    /**
     * The renderer paints the caller's controls after the pagination buttons, so a control in one of their columns
     * covers the nav icon, while the click router asks about a page flip before it asks about a control. The viewer
     * would see this button and get a page turn. It only bites on a canvas tall enough to paginate, so the guard is
     * uniform rather than page-count aware: the two columns are refused wherever the canvas ends up.
     */
    @Test
    void aControlInAPaginationColumnIsRefusedRatherThanCollidingWithThePageButtons() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GridSpec.Control(0, icon(), viewer -> {}))
                .withMessageContaining("page buttons");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GridSpec.Control(8, icon(), viewer -> {}))
                .withMessageContaining("page buttons");
    }

    @Test
    void theColumnsBetweenThePageButtonsAreAccepted() {
        assertThat(new GridSpec.Control(1, icon(), viewer -> {}).column()).isEqualTo(1);
        assertThat(new GridSpec.Control(7, icon(), viewer -> {}).column()).isEqualTo(7);
    }

    /**
     * The two reserved columns are named once, on the spec, and both this guard and the renderer's brush read them
     * from there. This is the test that would catch the two drifting apart: it names no column of its own, so it
     * follows the constants wherever they go, and a column that stops being reserved stops being refused here. The
     * tests above pin the columns the engine reserves today; this one pins that the guard and the brush read the same
     * two.
     */
    @Test
    void everyColumnOfTheRowTakesAControlExceptTheTwoTheSpecReserves() {
        for (int column = 0; column < GridSpec.COLUMNS; column++) {
            int here = column;
            if (column == GridSpec.PREV_COLUMN || column == GridSpec.NEXT_COLUMN) {
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> new GridSpec.Control(here, icon(), viewer -> {}))
                        .withMessageContaining("page buttons");
            } else {
                assertThat(new GridSpec.Control(here, icon(), viewer -> {}).column())
                        .isEqualTo(here);
            }
        }
    }

    // -- where a shift-clicked item lands ---------------------------------------------------------------------

    @Test
    void anEmptyCanvasAppendsAtTheFirstSlot() {
        assertThat(grid(3, Map.of()).firstEmptySlot()).hasValue(0);
    }

    @Test
    void aCanvasWithItsFirstSlotsTakenAppendsAfterThem() {
        assertThat(grid(3, Map.of(0, item, 1, item)).firstEmptySlot()).hasValue(2);
    }

    @Test
    void aGapLeftByARemovedItemIsFilledBeforeTheEnd() {
        assertThat(grid(3, Map.of(0, item, 2, item)).firstEmptySlot()).hasValue(1);
    }

    @Test
    void aFullCanvasHasNowhereToAppendRatherThanOverflowing() {
        Map<Integer, MenuItemSpec> full = new HashMap<>();
        for (int slot = 0; slot < 9; slot++) {
            full.put(slot, item);
        }

        assertThat(grid(1, full).firstEmptySlot()).isEmpty();
    }

    /** The scan stops at the chest slots, so an item recorded against a bottom-inventory slot cannot fill the grid. */
    @Test
    void anItemOnABottomSlotDoesNotCountAsFillingTheCanvas() {
        assertThat(grid(1, Map.of(60, item)).firstEmptySlot()).hasValue(0);
    }

    /**
     * The content is re-read on every ask, which is what lets the editor append one item, re-render, and have the
     * next append land on the slot after it without the spec being rebuilt.
     */
    @Test
    void appendingAnItemMovesTheNextAppendAlong() {
        Map<Integer, MenuItemSpec> content = new HashMap<>();
        GridSpec spec = new GridSpec(Component.text("t"), 3, () -> content, icon(), icon(), icon(), icon(), List.of());

        assertThat(spec.firstEmptySlot()).hasValue(0);
        content.put(0, item);
        assertThat(spec.firstEmptySlot()).hasValue(1);
    }
}

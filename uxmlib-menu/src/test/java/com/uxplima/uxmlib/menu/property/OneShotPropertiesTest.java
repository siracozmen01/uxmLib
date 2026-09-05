package com.uxplima.uxmlib.menu.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The three properties that do something once instead of holding a value, and the record that carries a list
 * sub-menu's geometry. What they share is that the module supplies the behaviour and the property supplies only the
 * threading and the redraw, so these tests are about which thread each hop lands on and what happens when the
 * module's own answer is no.
 */
class OneShotPropertiesTest {

    private final SameThreadScheduler scheduler = new SameThreadScheduler();

    private int reopens;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PropertyClick click() {
        return new PropertyClick(
                viewer,
                false,
                false,
                () -> reopens++,
                (who, title, rows, filler, buttons) -> {
                    throw new UnsupportedOperationException("a one-shot property opens no picker");
                },
                (who, title, onYes, onNo) -> {
                    throw new UnsupportedOperationException("a one-shot property opens no confirm");
                });
    }

    // -- a typed line is not covered here ---------------------------------------------------------------------
    //
    // TextProperty.applyInput is public so the validate-then-set behaviour is reachable without a live prompt, but
    // constructing the property still costs a TextInput that none of these assertions would touch. The reason now
    // lives in that method's own javadoc, which used to claim the coverage this comment would otherwise excuse.

    // -- a one-shot action ------------------------------------------------------------------------------------

    @Test
    void anActionPropertyRunsItsHandlerInlineWithTheViewerAndTheReopen() {
        AtomicReference<Player> got = new AtomicReference<>();
        ActionProperty property = ActionProperty.of("label", Material.PAPER, "menu.hint", (who, reopen) -> {
            got.set(who);
            reopen.run();
        });

        property.onClick(click());

        assertThat(got.get()).isSameAs(viewer);
        assertThat(reopens).isEqualTo(1);
        assertThat(scheduler.entityHops)
                .as("a plain action property marshals nothing; the handler decides its own threading")
                .isZero();
    }

    /** The hint may depend on the viewer, so a count or a state line can be shown on a button with no value. */
    @Test
    void anActionsHintMayBeComputedPerViewer() {
        ActionProperty property =
                new ActionProperty("label", Material.PAPER, who -> "for:" + who.getName(), (who, reopen) -> {});

        assertThat(property.valueLore(viewer)).isEqualTo("for:" + viewer.getName());
    }

    @Test
    void theFixedHintConvenienceGivesEveryViewerTheSameLine() {
        ActionProperty property = ActionProperty.of("label", Material.PAPER, "menu.hint", (who, reopen) -> {});

        assertThat(property.valueLore(viewer)).isEqualTo("menu.hint");
    }

    /**
     * The difference from a plain action property is the whole reason this class exists: the click is marshalled
     * onto the viewer's entity thread first, so a handler may read the player's live position before scheduling its
     * own write.
     */
    @Test
    void anActionButtonHopsToTheViewersThreadBeforeItsHandlerRuns() {
        List<String> order = new ArrayList<>();
        AbstractActionButton button = new AbstractActionButton(
                "label", Material.PAPER, "menu.hint", (who, reopen) -> order.add("handler"), scheduler) {};

        button.onClick(click());

        assertThat(scheduler.entityHops).isEqualTo(1);
        assertThat(order).containsExactly("handler");
    }

    @Test
    void anActionButtonsHintIsFixedBecauseTheButtonHasNoValueToShow() {
        AbstractActionButton button =
                new AbstractActionButton("label", Material.PAPER, "menu.hint", (who, reopen) -> {}, scheduler) {};

        assertThat(button.valueLore(viewer)).isEqualTo("menu.hint");
        assertThat(button.label()).isEqualTo("label");
        assertThat(button.icon()).isEqualTo(Material.PAPER);
    }

    // -- a list sub-menu's geometry ---------------------------------------------------------------------------

    private static ListPropertyLayout layout(int rows, List<Integer> entrySlots, int addSlot, int backSlot) {
        return new ListPropertyLayout(
                rows,
                entrySlots,
                addSlot,
                backSlot,
                Material.PAPER,
                Material.EMERALD,
                Material.ARROW,
                Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void aLayoutKeepsItsEntrySlotsInTheOrderTheFileNamedThem() {
        assertThat(layout(3, List.of(8, 0, 4), 1, 2).entrySlots()).containsExactly(8, 0, 4);
    }

    /** The record copies the list, so a caller mutating theirs afterwards cannot move an operator's buttons. */
    @Test
    void aLayoutDoesNotShareTheListItWasHandedAndCannotBeMutatedThrough() {
        List<Integer> mutable = new ArrayList<>(List.of(1, 2));
        ListPropertyLayout built = layout(3, mutable, 1, 2);

        mutable.add(3);

        assertThat(built.entrySlots()).containsExactly(1, 2);
        assertThatThrownBy(() -> built.entrySlots().add(9))
                .as("a layout parsed once is read by every open, so nothing may move its slots afterwards")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aWindowThatIsNotOneToSixRowsIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(0, List.of(1), 1, 2))
                .withMessageContaining("rows must be 1..6");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(7, List.of(1), 1, 2))
                .withMessageContaining("rows must be 1..6");
    }

    @Test
    void aLayoutWithNoEntrySlotsIsRefusedBecauseAListWithNowhereToDrawIsNotAList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(3, List.of(), 1, 2))
                .withMessageContaining("entrySlots must not be empty");
    }

    /** A slot past the end of the window it declares would simply not be drawn, so it is refused at parse time. */
    @Test
    void anAddOrBackSlotOutsideTheWindowIsRefusedRatherThanSilentlyNotDrawn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(1, List.of(0), 9, 2))
                .withMessageContaining("addSlot out of range");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(1, List.of(0), 1, -1))
                .withMessageContaining("backSlot out of range");
    }
}

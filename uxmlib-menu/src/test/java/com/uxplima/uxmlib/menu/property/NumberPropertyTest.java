package com.uxplima.uxmlib.menu.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A number the viewer steps with a click. The class documents that the editors stay unit-testable without forging a
 * Bukkit event, which is what these tests take it up on: every decision here is a click direction, a modifier and a
 * bound, and none of it needs a window.
 */
class NumberPropertyTest {

    private final SameThreadScheduler scheduler = new SameThreadScheduler();

    private final AtomicLong value = new AtomicLong(10);

    private final List<Long> written = new ArrayList<>();

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

    private NumberProperty property(long step, long shiftMultiplier, long min, long max) {
        return new NumberProperty(
                "label",
                Material.PAPER,
                value::get,
                step,
                shiftMultiplier,
                min,
                max,
                next -> {
                    written.add(next);
                    value.set(next);
                },
                scheduler);
    }

    private PropertyClick click(boolean right, boolean shift) {
        return new PropertyClick(
                viewer,
                right,
                shift,
                () -> reopens++,
                (who, title, rows, filler, buttons) -> {
                    throw new UnsupportedOperationException("a number property opens no picker");
                },
                (who, title, onYes, onNo) -> {
                    throw new UnsupportedOperationException("a number property opens no confirm");
                });
    }

    @Test
    void aLeftClickAddsAStepAndARightClickTakesOneAway() {
        property(5, 10, 0, 100).onClick(click(false, false));
        property(5, 10, 0, 100).onClick(click(true, false));

        assertThat(written).containsExactly(15L, 10L);
    }

    @Test
    void aShiftClickMultipliesTheStepSoACoarseJumpIsOneGesture() {
        property(5, 10, 0, 1000).onClick(click(false, true));

        assertThat(written).containsExactly(60L);
    }

    @Test
    void aStepOverTheCeilingLandsOnTheCeilingRatherThanBeingRefused() {
        property(5, 10, 0, 12).onClick(click(false, false));

        assertThat(written).containsExactly(12L);
    }

    @Test
    void aStepUnderTheFloorLandsOnTheFloor() {
        property(5, 10, 8, 100).onClick(click(true, false));

        assertThat(written).containsExactly(8L);
    }

    /** At a bound there is nothing to write, and writing it anyway would cost a database round trip per click. */
    @Test
    void aClickThatChangesNothingSkipsTheWriteAndJustRedraws() {
        value.set(100);

        property(5, 10, 0, 100).onClick(click(false, false));

        assertThat(written).isEmpty();
        assertThat(scheduler.asyncHops).isZero();
        assertThat(reopens).isEqualTo(1);
    }

    /**
     * The write goes off the tick thread and the redraw comes back to the viewer's own thread. Both hops matter: the
     * setter is a module's use case and may touch storage, and the redraw touches an inventory.
     */
    @Test
    void aWriteHopsOffTheTickThreadAndTheRedrawHopsBack() {
        property(5, 10, 0, 100).onClick(click(false, false));

        assertThat(scheduler.asyncHops).isEqualTo(1);
        assertThat(scheduler.entityHops).isEqualTo(1);
        assertThat(reopens).isEqualTo(1);
    }

    @Test
    void theValueLoreIsWhateverTheSupplierSaysRightNow() {
        NumberProperty property = property(5, 10, 0, 100);

        assertThat(property.valueLore(viewer)).isEqualTo("10");
        value.set(42);
        assertThat(property.valueLore(viewer)).isEqualTo("42");
    }

    @Test
    void aStepOfZeroIsRefusedAtWiringTimeBecauseAButtonThatDoesNothingIsABug() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> property(0, 10, 0, 100))
                .withMessageContaining("step must be > 0");
    }

    @Test
    void aMultiplierBelowOneIsRefusedBecauseShiftMustNotShrinkTheStep() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> property(5, 0, 0, 100))
                .withMessageContaining("shiftMultiplier must be >= 1");
    }

    @Test
    void anInvertedRangeIsRefusedRatherThanClampingEveryValueToTheSameNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> property(5, 10, 100, 0))
                .withMessageContaining("must be <=");
    }
}

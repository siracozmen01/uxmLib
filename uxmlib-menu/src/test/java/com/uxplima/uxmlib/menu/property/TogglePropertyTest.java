package com.uxplima.uxmlib.menu.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** A short cycle of states, walked in both directions so a viewer never has to go all the way round to go back. */
class TogglePropertyTest {

    private final SameThreadScheduler scheduler = new SameThreadScheduler();

    private final AtomicReference<String> value = new AtomicReference<>("low");

    private final List<String> written = new ArrayList<>();

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

    private ToggleProperty<String> property() {
        return new ToggleProperty<>(
                "label",
                Material.PAPER,
                List.of("low", "mid", "high"),
                value::get,
                (who, state) -> "shown:" + state,
                next -> {
                    written.add(next);
                    value.set(next);
                },
                scheduler);
    }

    private PropertyClick click(boolean right) {
        return new PropertyClick(
                viewer,
                right,
                false,
                () -> reopens++,
                (who, title, rows, filler, buttons) -> {
                    throw new UnsupportedOperationException("a toggle opens no picker");
                },
                (who, title, onYes, onNo) -> {
                    throw new UnsupportedOperationException("a toggle opens no confirm");
                });
    }

    @Test
    void aLeftClickAdvancesAndARightClickStepsBack() {
        property().onClick(click(false));
        property().onClick(click(true));

        assertThat(written).containsExactly("mid", "low");
    }

    @Test
    void theCycleWrapsInBothDirectionsSoNoStateIsAtADeadEnd() {
        value.set("high");
        property().onClick(click(false));
        assertThat(value.get()).isEqualTo("low");

        value.set("low");
        property().onClick(click(true));
        assertThat(value.get()).isEqualTo("high");
    }

    /**
     * A state the cycle does not hold is a feature whose stored value drifted from its declared set. Stepping from
     * the head is what lets the next click put it back on the cycle rather than leaving the viewer stuck.
     */
    @Test
    void aCurrentStateOutsideTheCycleStepsFromTheHeadRatherThanRefusing() {
        value.set("something-else");
        property().onClick(click(false));
        assertThat(value.get()).isEqualTo("mid");

        value.set("something-else");
        property().onClick(click(true));
        assertThat(value.get()).isEqualTo("high");
    }

    @Test
    void aWriteHopsOffTheTickThreadAndTheRedrawHopsBack() {
        property().onClick(click(false));

        assertThat(scheduler.asyncHops).isEqualTo(1);
        assertThat(scheduler.entityHops).isEqualTo(1);
        assertThat(reopens).isEqualTo(1);
    }

    /** No state name is ever an inline literal, so the display function is the only thing that words a state. */
    @Test
    void theValueLoreComesFromTheDisplayFunctionAndNotFromTheStatesOwnName() {
        assertThat(property().valueLore(viewer)).isEqualTo("shown:low");
    }

    @Test
    void aBooleanToggleIsFalseThenTrueSoALeftClickFromOffTurnsItOn() {
        AtomicReference<Boolean> flag = new AtomicReference<>(false);
        List<Boolean> writes = new ArrayList<>();
        ToggleProperty<Boolean> property = ToggleProperty.ofBoolean(
                "label", Material.PAPER, flag::get, (who, state) -> String.valueOf(state), writes::add, scheduler);

        property.onClick(click(false));

        assertThat(writes).containsExactly(true);
    }

    @Test
    void aCycleOfOneIsRefusedBecauseAToggleThatCannotToggleIsAButtonWithNoEffect() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToggleProperty<>(
                        "label",
                        Material.PAPER,
                        List.of("only"),
                        value::get,
                        (who, state) -> state,
                        written::add,
                        scheduler))
                .withMessageContaining("at least two states");
    }
}

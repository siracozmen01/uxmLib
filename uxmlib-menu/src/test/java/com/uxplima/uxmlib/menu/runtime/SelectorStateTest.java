package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmlib.menu.property.ChildClickHandler;
import org.junit.jupiter.api.Test;

/**
 * The routing of a selector child window. Every option slot carries its own handler and the handler is given the
 * gesture, so a list entry can tell a left click from a right one while an option button ignores both.
 *
 * <p>The state outlives the map it was built from, so the copy the constructor makes is the difference between a
 * window whose buttons are fixed at open and one whose buttons follow a caller's mutable map.
 */
class SelectorStateTest {

    private final List<String> ran = new ArrayList<>();

    private ChildClickHandler records(String name) {
        return (rightClick, shiftClick) -> ran.add(name + ":" + rightClick + ":" + shiftClick);
    }

    @Test
    void eachSlotAnswersWithTheHandlerDrawnThere() {
        SelectorState state = new SelectorState(Map.of(10, records("first"), 12, records("second")));

        state.chooseAt(10).orElseThrow().onClick(false, false);
        state.chooseAt(12).orElseThrow().onClick(true, true);

        assertThat(ran).containsExactly("first:false:false", "second:true:true");
    }

    @Test
    void aSlotWithNoButtonOnItIsNoChoice() {
        SelectorState state = new SelectorState(Map.of(10, records("first")));

        assertThat(state.chooseAt(11)).isEmpty();
        assertThat(state.chooseAt(0)).isEmpty();
    }

    /**
     * The buttons are fixed when the window opens. A caller that goes on editing the map it handed over is editing
     * its own map, not the open window, so a slot cannot grow a button after the window was drawn without it.
     */
    @Test
    void theButtonsAreCopiedSoALaterEditByTheCallerDoesNotReachTheOpenWindow() {
        Map<Integer, ChildClickHandler> choices = new HashMap<>();
        choices.put(10, records("first"));
        SelectorState state = new SelectorState(choices);

        choices.put(12, records("added later"));
        choices.remove(10);

        assertThat(state.chooseAt(12)).isEmpty();
        assertThat(state.chooseAt(10)).isPresent();
    }

    /**
     * A missing handler is named while the window is being built, not when a player clicks the slot. The click is
     * where the failure would be silent: the listener would find nothing at the slot and treat it as empty space.
     */
    @Test
    void aMissingHandlerIsNamedAtConstructionRatherThanAtTheClick() {
        Map<Integer, ChildClickHandler> withAHole = new HashMap<>();
        withAHole.put(10, null);

        assertThatNullPointerException()
                .isThrownBy(() -> new SelectorState(withAHole))
                .withMessageContaining("choice");
    }

    @Test
    void onlyTheFirstChoiceCounts() {
        SelectorState state = new SelectorState(Map.of(10, records("first")));

        assertThat(state.fire()).isTrue();
        assertThat(state.fire()).isFalse();
    }

    /** An empty selector is a window with no buttons rather than a failure: every slot answers with nothing. */
    @Test
    void aSelectorWithNoOptionsIsAWindowWithNoButtons() {
        SelectorState state = new SelectorState(Map.of());

        assertThat(state.chooseAt(10)).isEmpty();
        assertThat(state.fire()).isTrue();
    }
}

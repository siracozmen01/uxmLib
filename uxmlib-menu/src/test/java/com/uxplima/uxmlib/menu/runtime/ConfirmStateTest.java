package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The routing of a two-button window. There are only two slots and only one click that counts, so what is worth
 * pinning is that the two decisions never swap and that the second click does nothing: the window closes on the
 * first, and a stray second click arriving in the same tick would otherwise delete a thing twice.
 */
class ConfirmStateTest {

    private final List<String> ran = new ArrayList<>();

    private ConfirmState state(int yesSlot, int noSlot) {
        return new ConfirmState(yesSlot, noSlot, () -> ran.add("yes"), () -> ran.add("no"));
    }

    @Test
    void eachSlotAnswersWithItsOwnDecision() {
        ConfirmState state = state(11, 15);

        state.decisionAt(11).orElseThrow().run();
        state.decisionAt(15).orElseThrow().run();

        assertThat(ran).containsExactly("yes", "no");
    }

    /** A click on the window's empty space is not a decision, so it must not fall through to either button. */
    @Test
    void aSlotThatCarriesNeitherButtonIsNoDecisionAtAll() {
        ConfirmState state = state(11, 15);

        assertThat(state.decisionAt(0)).isEmpty();
        assertThat(state.decisionAt(12)).isEmpty();
        assertThat(state.decisionAt(-1)).isEmpty();
    }

    /**
     * The window closes on the first click, but the close lands a tick later. The guard is what stands between a
     * viewer who clicks twice quickly and a decision taken twice.
     */
    @Test
    void onlyTheFirstDecisionCounts() {
        ConfirmState state = state(11, 15);

        assertThat(state.fire()).isTrue();
        assertThat(state.fire()).isFalse();
        assertThat(state.fire()).isFalse();
    }

    /** The guard is on the window, not on the button, so a yes after a no is still a second decision. */
    @Test
    void theGuardCoversBothButtonsAndNotEachOne() {
        ConfirmState state = state(11, 15);

        assertThat(state.fire()).isTrue();
        assertThat(state.fire()).as("the other button is not a fresh decision").isFalse();
    }
}

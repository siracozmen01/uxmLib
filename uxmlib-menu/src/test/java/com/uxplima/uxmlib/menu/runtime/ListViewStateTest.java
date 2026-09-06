package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The slot routing one rendered page of an entity list leaves behind, so a click lands on the entity the viewer is
 * looking at rather than the one that was there a page ago. Everything here is about what a stale slot would do: the
 * list re-renders on every flip, and a record that outlives its page routes a click into an entity nobody clicked.
 */
class ListViewStateTest {

    @Test
    void theSpecTheListWasOpenedFromIsCarriedBackUnchanged() {
        Object spec = new Object();

        assertThat(new ListViewState(spec).spec()).isSameAs(spec);
    }

    @Test
    void aClickOnAnEntitySlotFindsTheEntityPaintedThere() {
        ListViewState state = new ListViewState(new Object());
        Object warp = "spawn";
        state.recordEntity(11, warp);

        assertThat(state.entityAt(11)).contains(warp);
        assertThat(state.entityAt(12))
                .as("a slot nothing was painted into routes nowhere")
                .isEmpty();
    }

    @Test
    void aClickOnAButtonSlotFindsTheActionRecordedThere() {
        ListViewState state = new ListViewState(new Object());
        int[] runs = {0};
        state.recordButton(4, () -> runs[0]++);

        state.buttonAt(4).orElseThrow().run();

        assertThat(runs[0]).isOne();
        assertThat(state.buttonAt(5)).isEmpty();
    }

    @Test
    void theNavSlotsAreTheOnesTheRendererPainted() {
        ListViewState state = new ListViewState(new Object());
        state.recordNav(45, 53);

        assertThat(state.isPrev(45)).isTrue();
        assertThat(state.isNext(53)).isTrue();
        assertThat(state.isPrev(53)).as("the two arrows are not each other").isFalse();
        assertThat(state.isNext(45)).isFalse();
    }

    @Test
    void aListThatPaintedNoArrowsClaimsNoSlot() {
        ListViewState state = new ListViewState(new Object());

        assertThat(state.isPrev(0))
                .as("a single page grows no arrows, and slot zero is an ordinary slot")
                .isFalse();
        assertThat(state.isNext(0)).isFalse();
    }

    @Test
    void clearingDropsEveryRecordedSlotSoAStaleClickRoutesNowhere() {
        ListViewState state = new ListViewState(new Object());
        state.recordEntity(11, "spawn");
        state.recordButton(4, () -> {});
        state.recordNav(45, 53);

        state.clearSlots();

        assertThat(state.entityAt(11)).isEmpty();
        assertThat(state.buttonAt(4)).isEmpty();
        assertThat(state.isPrev(45)).isFalse();
        assertThat(state.isNext(53)).isFalse();
    }

    @Test
    void clearingKeepsTheSpecBecauseTheNextPageIsDrawnFromIt() {
        Object spec = new Object();
        ListViewState state = new ListViewState(spec);

        state.clearSlots();

        assertThat(state.spec()).isSameAs(spec);
    }

    @Test
    void aSecondRenderIntoTheSameSlotReplacesWhatWasThere() {
        ListViewState state = new ListViewState(new Object());
        state.recordEntity(11, "spawn");

        state.recordEntity(11, "shop");

        assertThat(state.entityAt(11)).contains("shop");
    }

    @Test
    void aSecondNavRecordReplacesTheSlotsRatherThanAddingToThem() {
        ListViewState state = new ListViewState(new Object());
        state.recordNav(45, 53);

        state.recordNav(46, 52);

        assertThat(state.isPrev(45))
                .as("the arrows moved, and the slots they left are ordinary again")
                .isFalse();
        assertThat(state.isNext(53)).isFalse();
        assertThat(state.isPrev(46)).isTrue();
        assertThat(state.isNext(52)).isTrue();
    }
}

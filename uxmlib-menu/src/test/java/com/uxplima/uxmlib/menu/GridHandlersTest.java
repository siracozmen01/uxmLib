package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Which grid may take an operator's own item. Capture is the one gesture in the whole engine that is not
 * blanket-cancelled, so the default has to be the safe one: a grid opened without a capture handler must stay
 * cancelled like every other menu, and the short constructor is what nearly every caller uses.
 */
class GridHandlersTest {

    private static final GridClickHandler CLICKS = (view, player, menuSlot, filled, kind) -> {};

    private static final GridCaptureHandler CAPTURES = (view, viewer, menuSlot, copy) -> {};

    @Test
    void theShortConstructorLeavesAGridThatCannotBeCapturedInto() {
        assertThat(new GridHandlers(CLICKS).onCapture())
                .as("a capture handler nobody asked for would let items reach a cancelled window")
                .isNull();
    }

    @Test
    void aGridIsCaptureEnabledOnlyByBeingHandedAHandler() {
        assertThat(new GridHandlers(CLICKS, CAPTURES).onCapture()).isSameAs(CAPTURES);
    }

    @Test
    void theLongConstructorAcceptsNoCaptureAndMeansTheSameAsTheShortOne() {
        assertThat(new GridHandlers(CLICKS, null)).isEqualTo(new GridHandlers(CLICKS));
    }

    @Test
    void theClickHandlerIsCarriedThroughBothConstructors() {
        assertThat(new GridHandlers(CLICKS).onSlot()).isSameAs(CLICKS);
        assertThat(new GridHandlers(CLICKS, CAPTURES).onSlot()).isSameAs(CLICKS);
    }
}

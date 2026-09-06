package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.runtime.GridViewState.ContentSlot;
import org.junit.jupiter.api.Test;

/**
 * The slot routing of one open grid. It is what lets the single click listener tell a grid apart from every other menu
 * kind, so the property that matters most is that a re-render drops the old routing before it writes the new one: a
 * cell left behind from the previous page would hand a click a slot the window no longer draws.
 */
class GridViewStateTest {

    private final GridViewState state = new GridViewState("a spec", "some handlers");

    @Test
    void aFreshGridRoutesNothingAtAll() {
        assertThat(state.contentAt(0)).isEmpty();
        assertThat(state.controlAt(0)).isEmpty();
        assertThat(state.isPrev(0)).isFalse();
        assertThat(state.isNext(0)).isFalse();
    }

    @Test
    void aPaintedContentCellRemembersTheMenuSlotItDrewAndWhetherItHeldAnItem() {
        state.recordContent(11, 4, true);
        state.recordContent(12, 5, false);

        assertThat(state.contentAt(11)).contains(new ContentSlot(4, true));
        assertThat(state.contentAt(12)).contains(new ContentSlot(5, false));
        assertThat(state.contentAt(13))
                .as("a blocker slot carries no content cell")
                .isEmpty();
    }

    @Test
    void aControlButtonHandsBackTheVeryHandlerItWasRecordedWith() {
        Consumer<Player> handler = viewer -> {};
        state.recordControl(45, handler);

        assertThat(state.controlAt(45)).containsSame(handler);
    }

    @Test
    void aNavSlotDrawnThisPageIsRecognisedAndAnAbsentOneMatchesNothing() {
        state.recordNav(OptionalInt.empty(), OptionalInt.of(53));

        assertThat(state.isPrev(45))
                .as("a first page draws no previous button, so no slot may answer to it")
                .isFalse();
        assertThat(state.isNext(53)).isTrue();
        assertThat(state.isNext(52)).isFalse();
    }

    @Test
    void aReRenderDropsEveryCellControlAndNavSoAStaleClickCannotLand() {
        state.recordContent(11, 4, true);
        state.recordControl(45, viewer -> {});
        state.recordNav(OptionalInt.of(45), OptionalInt.of(53));

        state.clearSlots();

        assertThat(state.contentAt(11)).isEmpty();
        assertThat(state.controlAt(45)).isEmpty();
        assertThat(state.isPrev(45)).isFalse();
        assertThat(state.isNext(53)).isFalse();
    }

    @Test
    void theSpecAndHandlersAreCarriedOpaquelyAndHandedBackUnchanged() {
        Object spec = new Object();
        Object handlers = new Object();

        GridViewState carried = new GridViewState(spec, handlers);

        assertThat(carried.spec()).isSameAs(spec);
        assertThat(carried.handlers()).isSameAs(handlers);
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void aGridWithNoSpecOrNoHandlersIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new GridViewState(null, "handlers")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GridViewState("spec", null)).isInstanceOf(NullPointerException.class);
    }
}

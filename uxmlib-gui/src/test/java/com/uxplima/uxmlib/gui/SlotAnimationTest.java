package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the moving-highlight overlay: that advancing the animation writes only the changed slots (the
 * diff), that an unchanged tick writes nothing, and that the guarded clear never clobbers a slot a caller
 * placed an item into.
 */
class SlotAnimationTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack highlight() {
        return new ItemStack(Material.LIME_STAINED_GLASS_PANE);
    }

    /**
     * Records the slots written/cleared so a test can assert exactly which slots a tick touched. It models a
     * real cell: {@code occupied} is any non-empty cell, and {@code holding} is the subset that currently
     * shows the overlay's own highlight icon (so {@code holdsIcon} can distinguish our highlight from a
     * caller's button placed over it).
     */
    private static final class RecordingSink implements SlotAnimation.Sink {
        final List<Integer> lit = new ArrayList<>();
        final List<Integer> cleared = new ArrayList<>();
        private final java.util.Set<Integer> occupied;
        private final java.util.Set<Integer> holding = new java.util.HashSet<>();

        RecordingSink(Integer... occupiedSlots) {
            this.occupied = new java.util.HashSet<>(List.of(occupiedSlots));
        }

        /** Simulate a caller placing a real item (a button) into {@code slot} after the animation lit it. */
        void placeButton(int slot) {
            occupied.add(slot);
            holding.remove(slot);
        }

        @Override
        public boolean isFree(int slot) {
            return !occupied.contains(slot);
        }

        @Override
        public boolean holdsIcon(int slot, ItemStack icon) {
            return holding.contains(slot);
        }

        @Override
        public void light(int slot, ItemStack icon) {
            lit.add(slot);
            occupied.add(slot);
            holding.add(slot);
        }

        @Override
        public void clear(int slot) {
            cleared.add(slot);
            occupied.remove(slot);
            holding.remove(slot);
        }
    }

    @Test
    void firstAdvanceLightsTheStartingFrame() {
        SlotAnimation animation = SlotAnimation.of(SlotPattern.clockwiseBorder(3, 3, 1, 0), highlight());
        RecordingSink sink = new RecordingSink();

        animation.advance(0L, sink);

        assertThat(sink.lit).containsExactly(0); // first border slot
        assertThat(sink.cleared).isEmpty();
    }

    @Test
    void advanceClearsTheOldSlotAndLightsTheNew() {
        SlotAnimation animation = SlotAnimation.of(SlotPattern.clockwiseBorder(3, 3, 1, 0), highlight());
        RecordingSink sink = new RecordingSink();

        animation.advance(0L, sink); // lights slot 0
        sink.lit.clear();
        animation.advance(1L, sink); // moves to slot 1

        assertThat(sink.cleared).containsExactly(0); // old highlight removed
        assertThat(sink.lit).containsExactly(1); // new highlight placed
    }

    @Test
    void anUnchangedTickWritesNothing() {
        SlotAnimation animation = SlotAnimation.of(SlotPattern.clockwiseBorder(3, 3, 1, 0), highlight());
        RecordingSink sink = new RecordingSink();

        animation.advance(0L, sink); // frame 0
        sink.lit.clear();
        animation.advance(0L, sink); // same tick -> same frame, no change

        assertThat(sink.lit).isEmpty();
        assertThat(sink.cleared).isEmpty();
    }

    @Test
    void guardedClearLeavesAnOverlaidButtonAlone() {
        SlotAnimation animation = SlotAnimation.of(SlotPattern.clockwiseBorder(3, 3, 1, 0), highlight());
        // Slot 0 already holds a caller's button; the animation must not light it nor later clear it.
        RecordingSink sink = new RecordingSink(0);

        animation.advance(0L, sink); // wants slot 0 but it is occupied -> skip
        assertThat(sink.lit).isEmpty();

        animation.advance(1L, sink); // moves to slot 1, must not clear slot 0 it never lit
        assertThat(sink.cleared).isEmpty();
        assertThat(sink.lit).containsExactly(1);
    }

    @Test
    void aButtonPlacedOverAnOwnedSlotIsNeitherRepaintedNorCleared() {
        // Frame 0 lights {5,6}; frame 1 lights {5,7} (5 stays owned); frame 2 lights {8,9} (5 drops out).
        SlotAnimation animation =
                SlotAnimation.of(SlotPattern.of(List.of(List.of(5, 6), List.of(5, 7), List.of(8, 9))), highlight());
        RecordingSink sink = new RecordingSink();

        animation.advance(0L, sink); // lights 5 and 6
        assertThat(sink.lit).containsExactly(5, 6);

        // A caller now places a real button onto slot 5, which the overlay had lit.
        sink.placeButton(5);
        sink.lit.clear();
        sink.cleared.clear();

        animation.advance(1L, sink); // frame 1: 5 stays in-window but is now a button -> must NOT be re-lit
        assertThat(sink.lit).containsExactly(7); // only the genuinely new slot is lit
        assertThat(sink.cleared).doesNotContain(5);

        sink.lit.clear();
        sink.cleared.clear();
        animation.advance(2L, sink); // frame 2: 5 leaves the window -> must NOT be cleared (it's a button now)
        assertThat(sink.cleared).doesNotContain(5);
    }
}

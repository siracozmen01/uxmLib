package com.uxplima.uxmlib.gui;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * A moving-highlight overlay: a {@link SlotPattern} of lit slots plus the icon shown in them, advanced on a
 * tick clock. Each advance diffs the new frame against the last one and writes only the slots that changed
 * (clear the slots it lit last time that are now dark; light the slots in the new frame that are free).
 *
 * <p>The writes are guarded on every frame: a slot is lit only when it is free or still shows this overlay's
 * own highlight, and cleared only when it still shows that highlight. A button placed onto a lit slot after
 * the fact loses ownership, so the overlay never re-paints over it nor later clears it — the guarantee holds
 * even when an item is placed under the path after the animation lit it. The actual inventory writes go
 * through a {@link Sink}, so the frame/diff bookkeeping is testable without a live inventory and the menu
 * supplies the real write.
 */
public final class SlotAnimation {

    private final SlotPattern pattern;
    private final ItemStack icon;
    private @Nullable Integer lastFrame;
    private final Set<Integer> ownedSlots = new HashSet<>();

    private SlotAnimation(SlotPattern pattern, ItemStack icon) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.icon = Objects.requireNonNull(icon, "icon").clone();
    }

    /** Bind {@code pattern} to the highlight {@code icon}; the icon is cloned so later mutation is harmless. */
    public static SlotAnimation of(SlotPattern pattern, ItemStack icon) {
        return new SlotAnimation(pattern, icon);
    }

    /** The pattern this overlay animates. */
    public SlotPattern pattern() {
        return pattern;
    }

    /**
     * Advance to the frame for {@code ticks} and apply only the changed slots through {@code sink}. A no-op
     * when the frame has not changed since the last advance, so a menu that re-ticks at the same frame does
     * no inventory work.
     */
    public void advance(long ticks, Sink sink) {
        Objects.requireNonNull(sink, "sink");
        int frame = pattern.frameIndexAt(ticks);
        if (lastFrame != null && lastFrame == frame) {
            return;
        }
        clearOwnedNotIn(pattern.frame(frame), sink);
        lightFree(pattern.frame(frame), sink);
        lastFrame = frame;
    }

    private void clearOwnedNotIn(List<Integer> nextFrame, Sink sink) {
        Set<Integer> keep = new HashSet<>(nextFrame);
        for (Integer slot : new HashSet<>(ownedSlots)) {
            if (keep.contains(slot)) {
                continue;
            }
            // Only clear a slot still showing our highlight. If a button was placed over it since we lit it,
            // the cell no longer holds the icon, so we drop ownership and leave the caller's item untouched.
            if (sink.holdsIcon(slot, icon)) {
                sink.clear(slot);
            }
            ownedSlots.remove(slot);
        }
    }

    private void lightFree(List<Integer> nextFrame, Sink sink) {
        for (int slot : nextFrame) {
            // Re-check on every frame: light only a slot that is free or still shows our own highlight, never
            // one a caller has placed an item into since the last frame. A slot taken by a button loses
            // ownership so a later frame does not clear the button.
            if (sink.isFree(slot) || sink.holdsIcon(slot, icon)) {
                sink.light(slot, icon);
                ownedSlots.add(slot);
            } else {
                ownedSlots.remove(slot);
            }
        }
    }

    /**
     * The inventory writes an animation needs, abstracted so the diff is testable. {@link #isFree} guards
     * the lighting of a fresh slot (do not paint over a button), {@link #holdsIcon} re-checks an owned slot
     * still shows the overlay's highlight before it is re-lit or cleared (so a button placed over it after it
     * was lit is left alone), {@link #light} writes the highlight, {@link #clear} removes it. The animation
     * only ever calls {@link #clear} on a slot it lit that still holds its icon.
     */
    public interface Sink {

        /** Whether {@code slot} is empty and may be painted (a guard against clobbering a placed item). */
        boolean isFree(int slot);

        /**
         * Whether {@code slot} currently still shows the overlay's {@code icon} — i.e. nothing has been placed
         * over the highlight since it was lit. The overlay may re-light or clear only such a slot.
         */
        boolean holdsIcon(int slot, ItemStack icon);

        /** Show the highlight {@code icon} in {@code slot}. */
        void light(int slot, ItemStack icon);

        /** Remove the highlight from {@code slot} (called only for slots this animation lit). */
        void clear(int slot);
    }
}

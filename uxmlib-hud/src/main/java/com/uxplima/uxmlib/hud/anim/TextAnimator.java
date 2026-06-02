package com.uxplima.uxmlib.hud.anim;

import net.kyori.adventure.text.Component;

/**
 * A stateful producer of animated text. {@link #frame()} returns the component for the current step; calling
 * {@link #advance()} moves to the next step and the next {@code frame()} reflects it. Stepping is decoupled
 * from time so a caller advances it on the shared tick (e.g. a sidebar title or a bar), and the same animator
 * is unit-tested by advancing it directly and asserting the frame sequence.
 *
 * <p>Implementations are not thread-safe: drive each one from a single scheduler thread.
 */
public interface TextAnimator {

    /** The component for the current step. Pure: calling it repeatedly without {@link #advance()} is stable. */
    Component frame();

    /** Step to the next frame. */
    void advance();

    /** Return to the first frame. */
    void reset();
}

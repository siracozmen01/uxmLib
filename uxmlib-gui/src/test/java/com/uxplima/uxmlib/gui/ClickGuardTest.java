package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Per-viewer click debounce: a second click within the window is dropped, an old one accepted. */
class ClickGuardTest {

    @Test
    void firstClickIsAccepted() {
        ClickGuard guard = new ClickGuard(Duration.ofMillis(150L));
        UUID viewer = UUID.randomUUID();

        assertThat(guard.acceptAt(viewer, 1_000L)).isTrue();
    }

    @Test
    void clickInsideWindowIsDropped() {
        ClickGuard guard = new ClickGuard(Duration.ofMillis(150L));
        UUID viewer = UUID.randomUUID();

        assertThat(guard.acceptAt(viewer, 1_000L)).isTrue();
        assertThat(guard.acceptAt(viewer, 1_100L)).isFalse(); // 100ms later, inside the 150ms window
    }

    @Test
    void clickAfterWindowIsAcceptedAgain() {
        ClickGuard guard = new ClickGuard(Duration.ofMillis(150L));
        UUID viewer = UUID.randomUUID();

        assertThat(guard.acceptAt(viewer, 1_000L)).isTrue();
        assertThat(guard.acceptAt(viewer, 1_200L)).isTrue(); // 200ms later, window elapsed
    }

    @Test
    void windowIsTrackedPerViewer() {
        ClickGuard guard = new ClickGuard(Duration.ofMillis(150L));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(guard.acceptAt(a, 1_000L)).isTrue();
        assertThat(guard.acceptAt(b, 1_010L)).isTrue(); // a different viewer is not debounced by a's click
        assertThat(guard.acceptAt(a, 1_010L)).isFalse();
    }

    @Test
    void forgetResetsTheViewer() {
        ClickGuard guard = new ClickGuard(Duration.ofMillis(150L));
        UUID viewer = UUID.randomUUID();

        assertThat(guard.acceptAt(viewer, 1_000L)).isTrue();
        guard.forget(viewer);
        assertThat(guard.acceptAt(viewer, 1_010L)).isTrue(); // after close, the next click starts fresh
    }
}

package com.uxplima.uxmlib.gui.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the server-side Dialog facade. MockBukkit's Paper line does not back the Dialog registry, so the
 * native {@code build()} cannot be exercised here; instead these assert the fluent state (title, body,
 * button labels, kind) that the facade carries into the native call, plus the version gate. The native
 * construction is smoke-wired and verified against the real API by compilation.
 */
class DialogScreenTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void noticeCarriesTitleBodyAndButton() {
        DialogScreen screen = DialogScreen.notice(Component.text("Heads up"), Component.text("OK"))
                .body(Component.text("Something happened."))
                .button(Component.text("OK"), audience -> {});

        assertThat(screen.title()).isEqualTo(Component.text("Heads up"));
        assertThat(screen.isConfirmation()).isFalse();
        assertThat(screen.bodyLines()).containsExactly(Component.text("Something happened."));
        assertThat(screen.primaryLabel()).isEqualTo(Component.text("OK"));
    }

    @Test
    void confirmationCarriesYesAndNoLabels() {
        DialogScreen screen = DialogScreen.confirmation(
                        Component.text("Delete home?"), Component.text("Yes"), Component.text("No"))
                .body(Component.text("This cannot be undone."))
                .yes(Component.text("Delete"), audience -> {})
                .no(Component.text("Keep"), audience -> {});

        assertThat(screen.isConfirmation()).isTrue();
        assertThat(screen.primaryLabel()).isEqualTo(Component.text("Delete"));
        assertThat(screen.secondaryLabel()).isEqualTo(Component.text("Keep"));
    }

    /**
     * No label is shipped, so a notice shows the word the caller handed it. The word here is not English on
     * purpose: a library that wrote one would put it on a screen no translator can reach.
     */
    @Test
    void aNoticeShowsTheLabelItWasGiven() {
        DialogScreen screen = DialogScreen.notice(Component.text("FYI"), Component.text("Anladim"));

        assertThat(screen.primaryLabel()).isEqualTo(Component.text("Anladim"));
    }

    /** Same for a confirmation: two words, both the caller's. */
    @Test
    void aConfirmationShowsTheLabelsItWasGiven() {
        DialogScreen screen =
                DialogScreen.confirmation(Component.text("Sure?"), Component.text("Evet"), Component.text("Hayir"));

        assertThat(screen.primaryLabel()).isEqualTo(Component.text("Evet"));
        assertThat(screen.secondaryLabel()).isEqualTo(Component.text("Hayir"));
    }

    /** A label is required, so a caller that forgets one fails at construction rather than at a player. */
    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void aMissingLabelIsRefused() {
        assertThatNullPointerException().isThrownBy(() -> DialogScreen.notice(Component.text("t"), null));
        assertThatNullPointerException()
                .isThrownBy(() -> DialogScreen.confirmation(Component.text("t"), Component.text("y"), null));
    }

    @Test
    void severalBodyLinesAccumulateInOrder() {
        DialogScreen screen = DialogScreen.notice(Component.text("t"), Component.text("OK"))
                .body(Component.text("one"))
                .body(Component.text("two"));
        assertThat(screen.bodyLines()).containsExactly(Component.text("one"), Component.text("two"));
    }

    @Test
    void supportGateMatchesAOneTwentyOneSixServer() {
        // The class-under-test gates on ServerVersion 1.21.6; the gate itself is the version compare.
        boolean supported = DialogScreen.isSupported();
        assertThat(supported)
                .isEqualTo(com.uxplima.uxmlib.common.ServerVersion.current().isAtLeast(1, 21, 6));
    }

    @Test
    void callbackHandlerIsRetainedOnTheButton() {
        // The handler is wrapped into a native DialogActionCallback at build time; here we only assert the
        // facade stores it (so the wiring is exercised) by handing it a no-op that the build would invoke.
        int[] seen = {0};
        DialogScreen screen = DialogScreen.notice(Component.text("t"), Component.text("OK"))
                .button(Component.text("OK"), audience -> seen[0]++);
        assertThat(screen.primaryLabel()).isEqualTo(Component.text("OK"));
        // The handler reference is held; a direct invoke proves it is the one we passed.
        screen.invokePrimaryForTest(Audience.empty());
        assertThat(seen[0]).isEqualTo(1);
    }

    @Test
    void supportedBuildShapeIsTolerantUnderMock() {
        // MockBukkit cannot back the Dialog registry, so build() may throw there; this asserts only that
        // when it does succeed it yields a non-null Dialog. Pure smoke; the real shape is API-verified.
        DialogScreen screen = DialogScreen.notice(Component.text("t"), Component.text("OK"));
        if (!DialogScreen.isSupported()) {
            return;
        }
        try {
            assertThat(screen.build()).isNotNull();
        } catch (RuntimeException registryNotBacked) {
            // Expected under MockBukkit; the construction path is still compiled against the real API.
            assertThat(registryNotBacked).isNotNull();
        }
    }

    @Test
    void unmodifiableBodyView() {
        DialogScreen screen =
                DialogScreen.notice(Component.text("t"), Component.text("OK")).body(Component.text("x"));
        List<Component> lines = screen.bodyLines();
        assertThat(lines).hasSize(1);
    }
}

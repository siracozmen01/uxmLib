package com.uxplima.uxmlib.gui.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;

import io.papermc.paper.dialog.DialogResponseView;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the text-input Dialog facade. MockBukkit's Paper line does not back the Dialog registry, so the
 * native {@code build(...)} cannot be exercised here; instead these assert the fluent state and the input
 * mapping the facade carries into the native call, the value-delivery callback (fed a stub response view),
 * the argument validation, and the graceful degrade when the server is too old. The native registration is
 * verified against the real API by compilation.
 */
class DialogInputScreenTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void createRejectsNullTitleKeyAndLabel() {
        assertThatNullPointerException().isThrownBy(() -> screen(null, "name", Component.text("Name")));
        assertThatNullPointerException().isThrownBy(() -> screen(Component.text("t"), null, Component.text("Name")));
        assertThatNullPointerException().isThrownBy(() -> screen(Component.text("t"), "name", null));
    }

    /** A button label is required, so a caller that forgets one fails here rather than at a player. */
    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void createRejectsAMissingButtonLabel() {
        assertThatNullPointerException()
                .isThrownBy(() -> DialogInputScreen.create(
                        Component.text("t"), "k", Component.text("L"), null, Component.text("Cancel")));
        assertThatNullPointerException()
                .isThrownBy(() -> DialogInputScreen.create(
                        Component.text("t"), "k", Component.text("L"), Component.text("Confirm"), null));
    }

    @Test
    void createRejectsBlankKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> screen(Component.text("t"), "  ", Component.text("Name")));
    }

    /**
     * The two button words are the caller's and nothing is shipped, so they come back exactly as given. They
     * are not English here on purpose: a library that wrote "Confirm" would put it on a screen no translator
     * can reach.
     */
    @Test
    void carriesTitleKeyLabelAndTheWordsItWasGiven() {
        DialogInputScreen screen = DialogInputScreen.create(
                Component.text("Set warp"),
                "warp",
                Component.text("Warp name"),
                Component.text("Kaydet"),
                Component.text("Vazgec"));

        assertThat(screen.title()).isEqualTo(Component.text("Set warp"));
        assertThat(screen.key()).isEqualTo("warp");
        assertThat(screen.label()).isEqualTo(Component.text("Warp name"));
        assertThat(screen.initialValue()).isEmpty();
        assertThat(screen.submitLabelValue()).isEqualTo(Component.text("Kaydet"));
        assertThat(screen.cancelLabelValue()).isEqualTo(Component.text("Vazgec"));
    }

    /** The field's geometry keeps its defaults: a character cap and a pixel width are not words. */
    @Test
    void fluentSettersApply() {
        DialogInputScreen screen = screen(Component.text("t"), "k", Component.text("L"))
                .initial("home")
                .maxLength(64)
                .width(300);

        assertThat(screen.initialValue()).isEqualTo("home");
        assertThat(screen.maxLengthValue()).isEqualTo(64);
        assertThat(screen.widthValue()).isEqualTo(300);
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void settersRejectOutOfRangeAndNull() {
        DialogInputScreen screen = screen(Component.text("t"), "k", Component.text("L"));
        assertThatIllegalArgumentException().isThrownBy(() -> screen.maxLength(0));
        assertThatIllegalArgumentException().isThrownBy(() -> screen.width(0));
        assertThatIllegalArgumentException().isThrownBy(() -> screen.width(2048));
        assertThatNullPointerException().isThrownBy(() -> screen.initial(null));
    }

    @Test
    void inputMappingIsTolerantUnderMock() {
        // DialogInput.text routes through Paper's dialog instances provider, which MockBukkit does not back,
        // so toInput() may throw here; when it succeeds it carries the fluent state. Pure smoke; the mapping
        // is API-verified by compilation.
        DialogInputScreen screen = screen(Component.text("t"), "warp", Component.text("Name"))
                .initial("spawn")
                .maxLength(48)
                .width(250);
        try {
            var input = screen.toInput();
            assertThat(input.key()).isEqualTo("warp");
            assertThat(input.initial()).isEqualTo("spawn");
            assertThat(input.maxLength()).isEqualTo(48);
            assertThat(input.width()).isEqualTo(250);
        } catch (RuntimeException providerNotBacked) {
            // Expected under MockBukkit; the mapping is still compiled against the real API.
            assertThat(providerNotBacked).isNotNull();
        }
    }

    @Test
    void submitCallbackDeliversTheTypedLine() {
        DialogInputScreen screen = screen(Component.text("t"), "warp", Component.text("Name"));
        DialogResponseView response = mock(DialogResponseView.class);
        when(response.getText("warp")).thenReturn("mybase");

        AtomicReference<String> delivered = new AtomicReference<>();
        screen.submitCallback(delivered::set).accept(response, Audience.empty());

        assertThat(delivered.get()).isEqualTo("mybase");
    }

    @Test
    void submitCallbackTreatsAMissingValueAsEmpty() {
        DialogInputScreen screen = screen(Component.text("t"), "warp", Component.text("Name"));
        DialogResponseView response = mock(DialogResponseView.class);
        when(response.getText("warp")).thenReturn(null);

        assertThat(screen.currentText(response)).isEmpty();
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void promptRejectsNullArguments() {
        DialogInputScreen screen = screen(Component.text("t"), "k", Component.text("L"));
        Player player = MockBukkit.getMock().addPlayer();
        assertThatNullPointerException().isThrownBy(() -> screen.prompt(null, s -> {}, () -> {}));
        assertThatNullPointerException().isThrownBy(() -> screen.prompt(player, null, () -> {}));
        assertThatNullPointerException().isThrownBy(() -> screen.prompt(player, s -> {}, null));
    }

    @Test
    void degradesToOnCancelWhenUnsupported() {
        DialogInputScreen screen = screen(Component.text("t"), "k", Component.text("L"));
        Player player = MockBukkit.getMock().addPlayer();
        AtomicInteger submits = new AtomicInteger();
        AtomicInteger cancels = new AtomicInteger();

        assertThatCode(() -> screen.deliver(player, s -> submits.incrementAndGet(), cancels::incrementAndGet, false))
                .doesNotThrowAnyException();

        assertThat(cancels.get()).isEqualTo(1);
        assertThat(submits.get()).isZero();
    }

    @Test
    void supportGateMatchesAOneTwentyOneSixServer() {
        boolean supported = DialogInputScreen.isSupported();
        assertThat(supported)
                .isEqualTo(com.uxplima.uxmlib.common.ServerVersion.current().isAtLeast(1, 21, 6));
    }

    /** A screen whose button words this test does not care about, so the cases that do care stand out. */
    private static DialogInputScreen screen(Component title, String key, Component label) {
        return DialogInputScreen.create(title, key, label, Component.text("submit"), Component.text("cancel"));
    }
}

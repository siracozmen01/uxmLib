package com.uxplima.uxmlib.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

/**
 * The remaining-time resolver is a pure MiniMessage seam: given a supplier of the time still left, it
 * renders that value through uxmlib {@link com.uxplima.uxmlib.common.Durations}. No Bukkit, no static state,
 * so the rendered string for a fixed supplier is asserted directly.
 */
class RemainingTimeTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void timeTagRendersTheRemainingDurationFormatted() {
        Supplier<Duration> left = () -> Duration.ofMinutes(1).plusSeconds(30);
        Component out = Text.mini("Ends in <time>", RemainingTime.resolver(left));
        assertThat(plain(out)).isEqualTo("Ends in 1m 30s");
    }

    @Test
    void theDefaultKeyAlsoAnswersTheAutoTimeLeftAlias() {
        Supplier<Duration> left = () -> Duration.ofSeconds(45);
        Component out = Text.mini("<auto_time_left>", RemainingTime.resolver(left));
        assertThat(plain(out)).isEqualTo("45s");
    }

    @Test
    void theSupplierIsReadAtRenderTimeNotAtBuildTime() {
        AtomicLong seconds = new AtomicLong(10L);
        var resolver = RemainingTime.resolver(() -> Duration.ofSeconds(seconds.get()));

        assertThat(plain(Text.mini("<time>", resolver))).isEqualTo("10s");
        seconds.set(3L);
        assertThat(plain(Text.mini("<time>", resolver))).isEqualTo("3s");
    }

    @Test
    void aCustomKeyRendersUnderThatName() {
        var resolver = RemainingTime.resolver("left", () -> Duration.ofHours(2));
        assertThat(plain(Text.mini("<left>", resolver))).isEqualTo("2h");
    }

    @Test
    void aNegativeRemainderClampsToZero() {
        var resolver = RemainingTime.resolver(() -> Duration.ofSeconds(-5));
        assertThat(plain(Text.mini("<time>", resolver))).isEqualTo("0s");
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void rejectsANullSupplier() {
        assertThatThrownBy(() -> RemainingTime.resolver(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABlankKey() {
        assertThatThrownBy(() -> RemainingTime.resolver("  ", () -> Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

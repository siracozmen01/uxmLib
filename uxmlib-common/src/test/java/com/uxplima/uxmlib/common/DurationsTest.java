package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullUnmarked
class DurationsTest {

    @Test
    void parsesACompoundDuration() {
        assertThat(Durations.parse("1h30m")).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void parsesEveryUnit() {
        assertThat(Durations.parse("2d")).isEqualTo(Duration.ofDays(2));
        assertThat(Durations.parse("90s")).isEqualTo(Duration.ofSeconds(90));
        assertThat(Durations.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(Durations.parse("1d2h3m4s"))
                .isEqualTo(Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4));
    }

    @Test
    void toleratesWhitespaceBetweenTokens() {
        assertThat(Durations.parse("1d 2h 3m"))
                .isEqualTo(Duration.ofDays(1).plusHours(2).plusMinutes(3));
    }

    @Test
    void isUnitOrderIndependent() {
        assertThat(Durations.parse("30m1h")).isEqualTo(Durations.parse("1h30m"));
    }

    @Test
    void rejectsBlankAndMalformedInput() {
        assertThatThrownBy(() -> Durations.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("10x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("1h2x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWhitespaceOnlyLeadingGarbageAndSignedInput() {
        // The token match is anchored at each position, so a leading non-digit ("abc5m"), a sign ("-5m")
        // and a whitespace-only string are all rejected rather than partially parsed — the strict-rejection
        // contract a config loader relies on to surface a typo instead of silently using a wrong duration.
        assertThatThrownBy(() -> Durations.parse("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("abc5m")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Durations.parse("-5m")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Durations.tryParse("abc5m")).isEmpty();
    }

    @Test
    void parseAndTryParseRejectNull() {
        assertThatNullPointerException().isThrownBy(() -> Durations.parse(null));
        assertThatNullPointerException().isThrownBy(() -> Durations.tryParse(null));
    }

    @Test
    void tryParseReturnsEmptyInsteadOfThrowing() {
        assertThat(Durations.tryParse("nope")).isEmpty();
        assertThat(Durations.tryParse("")).isEmpty();
        assertThat(Durations.tryParse("5m")).contains(Duration.ofMinutes(5));
    }

    @Test
    void formatsOmittingZeroUnits() {
        assertThat(Durations.format(Duration.ofMinutes(90))).isEqualTo("1h 30m");
        assertThat(Durations.format(Duration.ofSeconds(3661))).isEqualTo("1h 1m 1s");
        assertThat(Durations.format(Duration.ofDays(2))).isEqualTo("2d");
    }

    @Test
    void formatsZeroAndSubSecond() {
        assertThat(Durations.format(Duration.ZERO)).isEqualTo("0s");
        assertThat(Durations.format(Duration.ofMillis(500))).isEqualTo("500ms");
    }

    @Test
    void roundTripsSecondGranularDurations() {
        Duration original = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4);
        assertThat(Durations.parse(Durations.format(original))).isEqualTo(original);
    }
}

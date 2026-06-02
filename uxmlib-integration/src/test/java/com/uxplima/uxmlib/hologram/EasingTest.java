package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Pure assertions of the Penner easing equations at the endpoints and the midpoint. Every curve must pin
 * {@code f(0)==0} and {@code f(1)==1}; the in-out variants pass through {@code (0.5, 0.5)}; and the known
 * closed-form midpoints are checked so a regression in any formula is caught without a server.
 */
class EasingTest {

    private static final double EPS = 1.0e-9;

    @Test
    void everyCurvePinsBothEndpoints() {
        for (Easing easing : Easing.values()) {
            assertThat(easing.apply(0.0)).as("%s at t=0", easing).isCloseTo(0.0, within(EPS));
            assertThat(easing.apply(1.0)).as("%s at t=1", easing).isCloseTo(1.0, within(EPS));
        }
    }

    @Test
    void linearIsTheIdentity() {
        assertThat(Easing.LINEAR.apply(0.5)).isCloseTo(0.5, within(EPS));
        assertThat(Easing.LINEAR.apply(0.25)).isCloseTo(0.25, within(EPS));
    }

    @Test
    void quadMidpoints() {
        assertThat(Easing.QUAD_IN.apply(0.5)).isCloseTo(0.25, within(EPS));
        assertThat(Easing.QUAD_OUT.apply(0.5)).isCloseTo(0.75, within(EPS));
        assertThat(Easing.QUAD_IN_OUT.apply(0.5)).isCloseTo(0.5, within(EPS));
    }

    @Test
    void cubicMidpoints() {
        assertThat(Easing.CUBIC_IN.apply(0.5)).isCloseTo(0.125, within(EPS));
        assertThat(Easing.CUBIC_OUT.apply(0.5)).isCloseTo(0.875, within(EPS));
        assertThat(Easing.CUBIC_IN_OUT.apply(0.5)).isCloseTo(0.5, within(EPS));
    }

    @Test
    void sineMidpoints() {
        // sin(pi/4) == cos(pi/4) == sqrt(2)/2
        double halfRootTwo = Math.sqrt(2.0) / 2.0;
        assertThat(Easing.SINE_IN.apply(0.5)).isCloseTo(1.0 - halfRootTwo, within(EPS));
        assertThat(Easing.SINE_OUT.apply(0.5)).isCloseTo(halfRootTwo, within(EPS));
        assertThat(Easing.SINE_IN_OUT.apply(0.5)).isCloseTo(0.5, within(EPS));
    }

    @Test
    void inOutVariantsAreSymmetricAboutTheCentre() {
        for (Easing easing : new Easing[] {Easing.QUAD_IN_OUT, Easing.CUBIC_IN_OUT, Easing.SINE_IN_OUT}) {
            double left = easing.apply(0.3);
            double right = easing.apply(0.7);
            assertThat(left + right).as("%s symmetry", easing).isCloseTo(1.0, within(EPS));
        }
    }

    @Test
    void progressIsClampedOutsideTheUnitInterval() {
        assertThat(Easing.CUBIC_IN.apply(-2.0)).isCloseTo(0.0, within(EPS));
        assertThat(Easing.CUBIC_IN.apply(5.0)).isCloseTo(1.0, within(EPS));
    }

    @Test
    void interpolateWalksFromStartToEndAlongTheCurve() {
        assertThat(Easing.LINEAR.interpolate(10.0, 20.0, 0.5)).isCloseTo(15.0, within(EPS));
        assertThat(Easing.QUAD_IN.interpolate(0.0, 4.0, 0.5)).isCloseTo(1.0, within(EPS));
        assertThat(Easing.CUBIC_OUT.interpolate(2.0, 2.0, 0.5)).isCloseTo(2.0, within(EPS));
    }
}

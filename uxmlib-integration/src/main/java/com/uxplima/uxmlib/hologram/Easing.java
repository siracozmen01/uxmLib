package com.uxplima.uxmlib.hologram;

/**
 * Easing curves for interpolated hologram motion. A native {@code setTeleportDuration} interpolates a move
 * linearly; to ease a {@code moveTo}/scale/rotation you step the transform over a short timer and pass each
 * step's progress through one of these curves to get a non-linear fraction.
 *
 * <p>The formulas are the canonical Robert Penner easing equations (public domain), re-expressed on a
 * normalised {@code [0,1]} progress: each {@link #apply} maps an input fraction {@code t} to an eased
 * fraction in {@code [0,1]}. Every curve satisfies {@code f(0) == 0} and {@code f(1) == 1}; the {@code IN_OUT}
 * variants are symmetric about {@code (0.5, 0.5)}. Re-implemented from the equations, not from any GPL source.
 */
public enum Easing {

    /** No easing: the eased fraction equals the input fraction. */
    LINEAR,

    /** Quadratic ease-in: slow start, {@code t^2}. */
    QUAD_IN,

    /** Quadratic ease-out: fast start, {@code 1 - (1 - t)^2}. */
    QUAD_OUT,

    /** Quadratic ease-in-out: slow at both ends, {@code 2t^2} then mirrored. */
    QUAD_IN_OUT,

    /** Cubic ease-in: {@code t^3}. */
    CUBIC_IN,

    /** Cubic ease-out: {@code 1 - (1 - t)^3}. */
    CUBIC_OUT,

    /** Cubic ease-in-out: {@code 4t^3} then mirrored. */
    CUBIC_IN_OUT,

    /** Sine ease-in: {@code 1 - cos(t*pi/2)}. */
    SINE_IN,

    /** Sine ease-out: {@code sin(t*pi/2)}. */
    SINE_OUT,

    /** Sine ease-in-out: {@code -(cos(pi*t) - 1) / 2}. */
    SINE_IN_OUT;

    /**
     * The eased fraction for an input progress {@code t}. {@code t} is clamped to {@code [0,1]} first, so a
     * caller that overshoots (a timer firing one tick late) still gets a sane endpoint rather than an
     * out-of-range value.
     */
    public double apply(double t) {
        double x = Math.max(0.0, Math.min(1.0, t));
        return switch (this) {
            case LINEAR -> x;
            case QUAD_IN -> x * x;
            case QUAD_OUT -> 1.0 - (1.0 - x) * (1.0 - x);
            case QUAD_IN_OUT -> x < 0.5 ? 2.0 * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 2.0) / 2.0;
            case CUBIC_IN -> x * x * x;
            case CUBIC_OUT -> 1.0 - Math.pow(1.0 - x, 3.0);
            case CUBIC_IN_OUT -> x < 0.5 ? 4.0 * x * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, 3.0) / 2.0;
            case SINE_IN -> 1.0 - Math.cos((x * Math.PI) / 2.0);
            case SINE_OUT -> Math.sin((x * Math.PI) / 2.0);
            case SINE_IN_OUT -> -(Math.cos(Math.PI * x) - 1.0) / 2.0;
        };
    }

    /**
     * Linearly interpolate from {@code from} to {@code to} at eased progress {@code t}. Convenience for
     * stepping a scalar (a scale factor, a coordinate) along this curve: {@code from + (to - from) * f(t)}.
     */
    public double interpolate(double from, double to, double t) {
        return from + (to - from) * apply(t);
    }
}

package com.uxplima.uxmlib.hologram.pool;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Exercises the single pure gate that decides whether one viewer should currently see a hologram: the
 * same-world / within-range / inside-the-FOV-cone check the two-set lifecycle reconciles against. The eye
 * direction is supplied directly as a vector so the cone math is testable without a live player look.
 */
class VisibilityGateTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rangeOnlyGateMatchesTheSameWorldRadiusCheck() {
        World world = server.addSimpleWorld("gate");
        VisibilityGate gate = VisibilityGate.range(10.0); // radius 10 -> 100 squared
        Location holo = new Location(world, 6, 64, 8); // 36 + 64 = 100 <= 10^2
        Location eye = new Location(world, 0, 64, 0);

        assertThat(gate.shouldShow(eye, holo)).isTrue();
    }

    @Test
    void rangeGateHidesBeyondRadius() {
        World world = server.addSimpleWorld("gate");
        VisibilityGate gate = VisibilityGate.range(10.0);
        Location holo = new Location(world, 9, 64, 9); // 162 > 100
        Location eye = new Location(world, 0, 64, 0);

        assertThat(gate.shouldShow(eye, holo)).isFalse();
    }

    @Test
    void rangeGateHidesAcrossWorldsWithoutThrowing() {
        World a = server.addSimpleWorld("a");
        World b = server.addSimpleWorld("b");
        VisibilityGate gate = VisibilityGate.range(10.0);
        Location holo = new Location(b, 0, 64, 0);
        Location eye = new Location(a, 0, 64, 0);

        assertThat(gate.shouldShow(eye, holo)).isFalse();
    }

    @Test
    void fovGateShowsWhenLookingTowardTheHologram() {
        World world = server.addSimpleWorld("gate");
        VisibilityGate gate = VisibilityGate.rangeAndFov(20.0, 60.0); // radius 20 -> well in range
        Location holo = new Location(world, 0, 64, 10); // straight ahead on +Z
        Location eye = new Location(world, 0, 64, 0, 0f, 0f); // yaw 0 looks toward +Z

        assertThat(gate.shouldShow(eye, holo)).isTrue();
    }

    @Test
    void fovGateHidesWhenLookingAway() {
        World world = server.addSimpleWorld("gate");
        VisibilityGate gate = VisibilityGate.rangeAndFov(20.0, 60.0);
        Location holo = new Location(world, 0, 64, 10); // behind the viewer
        Location eye = new Location(world, 0, 64, 0, 180f, 0f); // yaw 180 looks toward -Z

        assertThat(gate.shouldShow(eye, holo)).isFalse();
    }

    @Test
    void fovGateStillRangeCullsEvenWhenLookingAtIt() {
        World world = server.addSimpleWorld("gate");
        VisibilityGate gate = VisibilityGate.rangeAndFov(4.0, 90.0);
        Location holo = new Location(world, 0, 64, 10); // dead ahead but far
        Location eye = new Location(world, 0, 64, 0, 0f, 0f);

        assertThat(gate.shouldShow(eye, holo)).isFalse();
    }

    @Test
    void zeroLengthOffsetIsAlwaysInsideTheCone() {
        // The viewer eye and hologram occupy the same point: the look direction is undefined, so the cone
        // must not reject it (no divide-by-zero, no spurious hide).
        World world = server.addSimpleWorld("gate");
        VisibilityGate gate = VisibilityGate.rangeAndFov(4.0, 60.0);
        Location holo = new Location(world, 0, 64, 0);
        Location eye = new Location(world, 0, 64, 0, 0f, 0f);

        assertThat(gate.shouldShow(eye, holo)).isTrue();
    }
}

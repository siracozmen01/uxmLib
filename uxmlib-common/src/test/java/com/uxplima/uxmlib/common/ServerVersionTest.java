package com.uxplima.uxmlib.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Bukkit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ServerVersionTest {

    @Test
    void parsesTheYearBasedSchemeAndOrdersItAboveTheOldLine() {
        // Minecraft left "1.x.y" behind after 1.21.11 and now ships "26.1", "26.2", … Nothing about the
        // parse or the compare needs to know that: the year simply lands in `major`, and because 26 > 1
        // every release on the new line still orders above every release on the old one.
        ServerVersion yearBased = ServerVersion.parse("26.2");
        assertThat(yearBased.major()).isEqualTo(26);
        assertThat(yearBased.minor()).isEqualTo(2);
        assertThat(yearBased.patch()).isZero();
        assertThat(yearBased).isGreaterThan(ServerVersion.parse("1.21.11"));
        assertThat(yearBased.isAtLeast(1, 21, 0)).isTrue();
        assertThat(ServerVersion.parse("26.1.2")).isLessThan(yearBased);
    }

    @Test
    void parsesMajorMinorPatch() {
        ServerVersion version = ServerVersion.parse("1.21.4");
        assertThat(version.major()).isEqualTo(1);
        assertThat(version.minor()).isEqualTo(21);
        assertThat(version.patch()).isEqualTo(4);
    }

    @Test
    void treatsMissingPatchAsZero() {
        assertThat(ServerVersion.parse("1.21")).isEqualTo(ServerVersion.of(1, 21, 0));
    }

    @Test
    void ignoresTrailingQualifier() {
        assertThat(ServerVersion.parse("1.21.11-R0.1-SNAPSHOT")).isEqualTo(ServerVersion.of(1, 21, 11));
        assertThat(ServerVersion.parse("1.21.4-pre2")).isEqualTo(ServerVersion.of(1, 21, 4));
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> ServerVersion.parse("not-a-version")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServerVersion.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isAtLeastComparesLexicographically() {
        ServerVersion v = ServerVersion.of(1, 21, 4);
        assertThat(v.isAtLeast(1, 21, 4)).isTrue();
        assertThat(v.isAtLeast(1, 21, 3)).isTrue();
        assertThat(v.isAtLeast(1, 20, 6)).isTrue();
        assertThat(v.isAtLeast(1, 21, 5)).isFalse();
        assertThat(v.isAtLeast(1, 22, 0)).isFalse();
        assertThat(v.isAtLeast(2, 0, 0)).isFalse();
    }

    @Test
    void isAtLeastHandlesTheTwoArgPatchDefault() {
        ServerVersion v = ServerVersion.of(1, 21, 0);
        assertThat(v.isAtLeast(1, 21)).isTrue();
        assertThat(v.isAtLeast(1, 20)).isTrue();
        assertThat(v.isAtLeast(1, 22)).isFalse();
    }

    @Test
    void rejectsNegativeComponents() {
        assertThatThrownBy(() -> ServerVersion.of(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServerVersion.of(1, -1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServerVersion.of(1, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rendersAStableString() {
        assertThat(ServerVersion.of(1, 21, 4).toString()).isEqualTo("1.21.4");
    }

    // The running-server probe needs a mocked server; MockBukkit reports 1.21.x.
    @Nested
    class RunningProbe {

        @BeforeEach
        void setUp() {
            MockBukkit.mock();
        }

        @AfterEach
        void tearDown() {
            MockBukkit.unmock();
        }

        @Test
        void readsTheRunningVersionFromBukkit() {
            ServerVersion current = ServerVersion.current();
            assertThat(current).isEqualTo(ServerVersion.parse(Bukkit.getMinecraftVersion()));
            // The floor the library supports, asserted as the literal it is. There is one server line, so
            // there is nothing left for a loose bound to keep the test portable across.
            assertThat(current.isAtLeast(26, 2, 0)).isTrue();
        }

        @Test
        void cachesTheRunningVersion() {
            assertThat(ServerVersion.current()).isSameAs(ServerVersion.current());
        }

        @Test
        void notRunningUnderFolia() {
            // MockBukkit is plain Paper; the Folia marker class is absent.
            assertThat(ServerVersion.isFolia()).isFalse();
        }
    }
}

package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SemanticVersionTest {

    @Test
    void parsesPlainTriple() {
        SemanticVersion v = SemanticVersion.parse("1.4.0");
        assertThat(v.toString()).isEqualTo("1.4.0");
    }

    @Test
    void stripsLeadingVPrefix() {
        assertThat(SemanticVersion.parse("v2.0.0")).isEqualTo(SemanticVersion.parse("2.0.0"));
    }

    @Test
    void dropsBuildMetadataAndSnapshotSuffix() {
        // Gradle's "-SNAPSHOT" and SemVer "+build" metadata must not affect ordering.
        assertThat(SemanticVersion.parse("1.2.3+build.99")).isEqualTo(SemanticVersion.parse("1.2.3"));
        assertThat(SemanticVersion.parse("1.2.3-SNAPSHOT").release()).isEqualTo(SemanticVersion.parse("1.2.3"));
    }

    @Test
    void missingMinorOrPatchTreatedAsZero() {
        assertThat(SemanticVersion.parse("1")).isEqualTo(SemanticVersion.parse("1.0.0"));
        assertThat(SemanticVersion.parse("1.5")).isEqualTo(SemanticVersion.parse("1.5.0"));
    }

    @Test
    void numericOrderingNotLexical() {
        // 10 > 9 numerically; a string compare would get this wrong.
        assertThat(SemanticVersion.parse("1.10.0")).isGreaterThan(SemanticVersion.parse("1.9.0"));
        assertThat(SemanticVersion.parse("2.0.0")).isGreaterThan(SemanticVersion.parse("1.99.99"));
    }

    @Test
    void preReleaseIsLowerThanRelease() {
        assertThat(SemanticVersion.parse("1.0.0-alpha")).isLessThan(SemanticVersion.parse("1.0.0"));
        assertThat(SemanticVersion.parse("1.0.0")).isGreaterThan(SemanticVersion.parse("1.0.0-rc.1"));
    }

    @Test
    void preReleaseLadderOrdering() {
        // The canonical SemVer pre-release ladder.
        assertThat(SemanticVersion.parse("1.0.0-alpha")).isLessThan(SemanticVersion.parse("1.0.0-alpha.1"));
        assertThat(SemanticVersion.parse("1.0.0-alpha.1")).isLessThan(SemanticVersion.parse("1.0.0-alpha.beta"));
        assertThat(SemanticVersion.parse("1.0.0-alpha.beta")).isLessThan(SemanticVersion.parse("1.0.0-beta"));
        assertThat(SemanticVersion.parse("1.0.0-beta")).isLessThan(SemanticVersion.parse("1.0.0-beta.2"));
        assertThat(SemanticVersion.parse("1.0.0-beta.2")).isLessThan(SemanticVersion.parse("1.0.0-beta.11"));
        assertThat(SemanticVersion.parse("1.0.0-beta.11")).isLessThan(SemanticVersion.parse("1.0.0-rc.1"));
        assertThat(SemanticVersion.parse("1.0.0-rc.1")).isLessThan(SemanticVersion.parse("1.0.0"));
    }

    @Test
    void numericPreReleaseIdentifierLowerThanAlphanumeric() {
        // Per SemVer: numeric identifiers always have lower precedence than alphanumeric ones.
        assertThat(SemanticVersion.parse("1.0.0-1")).isLessThan(SemanticVersion.parse("1.0.0-alpha"));
    }

    @Test
    void equalVersionsCompareZero() {
        assertThat(SemanticVersion.parse("3.2.1").compareTo(SemanticVersion.parse("3.2.1")))
                .isZero();
    }

    @Test
    void isNewerThanReflectsOrdering() {
        assertThat(SemanticVersion.parse("1.4.0").isNewerThan(SemanticVersion.parse("1.3.9")))
                .isTrue();
        assertThat(SemanticVersion.parse("1.4.0").isNewerThan(SemanticVersion.parse("1.4.0")))
                .isFalse();
        assertThat(SemanticVersion.parse("1.4.0-beta").isNewerThan(SemanticVersion.parse("1.4.0")))
                .isFalse();
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> SemanticVersion.parse("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonNumericMajor() {
        assertThatThrownBy(() -> SemanticVersion.parse("x.y.z")).isInstanceOf(IllegalArgumentException.class);
    }
}

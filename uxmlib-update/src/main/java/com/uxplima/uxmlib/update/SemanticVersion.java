package com.uxplima.uxmlib.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A semantic version with a pre-release ladder, used to decide whether a {@link Release} is newer than the
 * running build. Parsing is lenient about the shapes real projects publish: an optional {@code v} prefix,
 * missing minor/patch (treated as zero), a {@code -SNAPSHOT}/{@code -rc.1} pre-release tail, and {@code +build}
 * metadata (ignored for ordering, as SemVer requires). Comparison follows SemVer precedence: numeric core
 * first, then a pre-release version always ranks below its release, and pre-release identifiers compare
 * field-by-field with numeric identifiers ranking below alphanumeric ones.
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

    private final int major;
    private final int minor;
    private final int patch;
    private final List<String> preRelease;

    private SemanticVersion(int major, int minor, int patch, List<String> preRelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = List.copyOf(preRelease);
    }

    /** Parse a version string. Throws {@link IllegalArgumentException} if the numeric core is unreadable. */
    public static SemanticVersion parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String text = raw.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        int plus = text.indexOf('+');
        if (plus >= 0) {
            text = text.substring(0, plus);
        }
        String core = text;
        List<String> pre = List.of();
        int dash = text.indexOf('-');
        if (dash >= 0) {
            core = text.substring(0, dash);
            pre = splitIdentifiers(text.substring(dash + 1));
        }
        int[] numbers = parseCore(core);
        return new SemanticVersion(numbers[0], numbers[1], numbers[2], pre);
    }

    private static List<String> splitIdentifiers(String tail) {
        List<String> out = new ArrayList<>();
        for (String part : tail.split("\\.", -1)) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    private static int[] parseCore(String core) {
        String[] parts = core.split("\\.", -1);
        int[] numbers = new int[3];
        for (int i = 0; i < 3; i++) {
            numbers[i] = i < parts.length ? parseNumber(parts[i]) : 0;
        }
        return numbers;
    }

    private static int parseNumber(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("version number must not be negative: " + value);
            }
            return parsed;
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("version core must be numeric: " + value, notANumber);
        }
    }

    /** This version stripped of any pre-release tail (its release counterpart). */
    public SemanticVersion release() {
        return new SemanticVersion(major, minor, patch, List.of());
    }

    /** Whether this version sorts strictly after {@code other}. */
    public boolean isNewerThan(SemanticVersion other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        Objects.requireNonNull(other, "other");
        int core = compareCore(other);
        if (core != 0) {
            return core;
        }
        return comparePreRelease(other);
    }

    private int compareCore(SemanticVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    private int comparePreRelease(SemanticVersion other) {
        // A version with no pre-release outranks one that has it; otherwise compare identifier by identifier.
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) {
            return 0;
        }
        if (preRelease.isEmpty()) {
            return 1;
        }
        if (other.preRelease.isEmpty()) {
            return -1;
        }
        int shared = Math.min(preRelease.size(), other.preRelease.size());
        for (int i = 0; i < shared; i++) {
            int byIdentifier = compareIdentifier(preRelease.get(i), other.preRelease.get(i));
            if (byIdentifier != 0) {
                return byIdentifier;
            }
        }
        // All shared identifiers equal: the longer pre-release set has higher precedence.
        return Integer.compare(preRelease.size(), other.preRelease.size());
    }

    private static int compareIdentifier(String a, String b) {
        boolean aNumeric = isNumeric(a);
        boolean bNumeric = isNumeric(b);
        if (aNumeric && bNumeric) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        // Numeric identifiers always have lower precedence than alphanumeric ones.
        if (aNumeric) {
            return -1;
        }
        if (bNumeric) {
            return 1;
        }
        return a.compareTo(b);
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SemanticVersion other)) {
            return false;
        }
        return major == other.major
                && minor == other.minor
                && patch == other.patch
                && preRelease.equals(other.preRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        String core = major + "." + minor + "." + patch;
        return preRelease.isEmpty() ? core : core + "-" + String.join(".", preRelease);
    }
}

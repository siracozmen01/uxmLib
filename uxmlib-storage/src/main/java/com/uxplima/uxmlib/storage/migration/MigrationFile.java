package com.uxplima.uxmlib.storage.migration;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed migration resource name in the Flyway-style {@code V<version>__<description>.sql} convention: the
 * {@code version} (an int), the human {@code description} (underscores become spaces), and the original
 * {@code fileName} used to load the resource. {@link #parse} returns empty for anything that is not a
 * migration file, so a directory may hold a README or other files without breaking discovery.
 */
record MigrationFile(int version, String description, String fileName) {

    // Bounded to nine digits so the captured version always fits an int (max 999_999_999 < 2^31), letting
    // parse() use Integer.parseInt without guarding against overflow. Case-insensitive so a .SQL extension
    // (or a lowercase v) still matches.
    private static final Pattern PATTERN = Pattern.compile("V(\\d{1,9})__(.+)\\.sql", Pattern.CASE_INSENSITIVE);

    MigrationFile {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(fileName, "fileName");
    }

    /** Parse a resource file name, or empty if it is not a {@code V<version>__<description>.sql} migration. */
    static Optional<MigrationFile> parse(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        Matcher matcher = PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int version = Integer.parseInt(Objects.requireNonNull(matcher.group(1)));
        String description = Objects.requireNonNull(matcher.group(2)).replace('_', ' ');
        return Optional.of(new MigrationFile(version, description, fileName));
    }
}

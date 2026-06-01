package com.uxplima.uxmlib.config;

import java.util.List;

/**
 * The outcome of validating a config: every {@link ConfigViolation} found, or none. Aggregating rather
 * than failing on the first problem lets an operator fix the whole file in one pass. Check {@link #ok()}
 * or hand {@link #violations()} to a logger.
 */
public record ValidationResult(List<ConfigViolation> violations) {

    public ValidationResult {
        violations = List.copyOf(violations);
    }

    /** A passing result with no violations. */
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /** Whether the config passed (no violations). */
    public boolean ok() {
        return violations.isEmpty();
    }

    /** Throw a {@link ConfigException} listing every violation if any were found. */
    public void throwIfInvalid() {
        if (!ok()) {
            StringBuilder message = new StringBuilder("invalid config:");
            for (ConfigViolation violation : violations) {
                message.append("\n  - ").append(violation);
            }
            throw new ConfigException(message.toString());
        }
    }
}

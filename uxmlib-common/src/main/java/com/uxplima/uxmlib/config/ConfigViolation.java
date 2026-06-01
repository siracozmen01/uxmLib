package com.uxplima.uxmlib.config;

/**
 * One thing wrong with a config value: the dotted path that failed and why. Collected into a
 * {@link ValidationResult} so a config is validated all at once and the operator sees every problem in a
 * single report, not just the first.
 */
public record ConfigViolation(String path, String message) {

    public ConfigViolation {
        java.util.Objects.requireNonNull(path, "path");
        java.util.Objects.requireNonNull(message, "message");
    }

    @Override
    public String toString() {
        return path + ": " + message;
    }
}

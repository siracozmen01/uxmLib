package com.uxplima.uxmlib.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Declares validation rules for a config and runs them all, collecting every failure into a
 * {@link ValidationResult} rather than throwing on the first. Build a rule set fluently against dotted
 * paths — require a key is present, bound a number to a range, match a string to a pattern, restrict to a
 * set of allowed values — then {@link #validate(HoconConfig)}. Aggregating means the operator sees every
 * problem in one report.
 */
public final class ConfigRules {

    @FunctionalInterface
    private interface Rule {
        void check(HoconConfig config, List<ConfigViolation> out);
    }

    private final List<Rule> rules = new ArrayList<>();

    /** Require a value to be present at {@code path}. */
    public ConfigRules require(String path) {
        Objects.requireNonNull(path, "path");
        rules.add((config, out) -> {
            if (config.getString(path).isEmpty()) {
                out.add(new ConfigViolation(path, "is required but missing"));
            }
        });
        return this;
    }

    /** Require the int at {@code path} to be within [{@code min}, {@code max}]. */
    public ConfigRules range(String path, int min, int max) {
        Objects.requireNonNull(path, "path");
        rules.add((config, out) -> {
            int value = config.getInt(path, min);
            if (value < min || value > max) {
                out.add(new ConfigViolation(path, "must be between " + min + " and " + max + " (was " + value + ")"));
            }
        });
        return this;
    }

    /** Require the string at {@code path} to match {@code regex}. */
    public ConfigRules matches(String path, String regex) {
        Objects.requireNonNull(path, "path");
        Pattern pattern = Pattern.compile(regex);
        rules.add((config, out) -> config.getString(path).ifPresent(value -> {
            if (!pattern.matcher(value).matches()) {
                out.add(new ConfigViolation(path, "must match " + regex + " (was '" + value + "')"));
            }
        }));
        return this;
    }

    /** Require the string at {@code path} to be one of {@code allowed}. */
    public ConfigRules oneOf(String path, String... allowed) {
        Objects.requireNonNull(path, "path");
        List<String> options = List.of(allowed);
        rules.add((config, out) -> config.getString(path).ifPresent(value -> {
            if (!options.contains(value)) {
                out.add(new ConfigViolation(path, "must be one of " + options + " (was '" + value + "')"));
            }
        }));
        return this;
    }

    /** Run every rule against {@code config}, collecting all violations. */
    public ValidationResult validate(HoconConfig config) {
        Objects.requireNonNull(config, "config");
        List<ConfigViolation> violations = new ArrayList<>();
        for (Rule rule : rules) {
            rule.check(config, violations);
        }
        return new ValidationResult(violations);
    }
}

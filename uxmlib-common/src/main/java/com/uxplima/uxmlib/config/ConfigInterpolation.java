package com.uxplima.uxmlib.config;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Resolves {@code ${path.to.key}} references inside string values against the config tree itself, with an
 * optional extra variables map taking precedence. References chain — a value that resolves to another
 * reference is followed — and a reference loop ({@code a -> b -> a}) throws a clear {@link ConfigException}
 * instead of recursing forever. An unknown reference is left verbatim so a typo is visible, not silently
 * blanked. Kept out of {@code HoconConfig} so that class stays within its size cap.
 */
final class ConfigInterpolation {

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

    private ConfigInterpolation() {}

    /** Rewrite every string scalar under {@code root}, resolving its references in place. */
    static void interpolate(ConfigurationNode root, Map<String, String> variables) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(variables, "variables");
        visit(root, root, variables);
    }

    /** Resolve the references in a single {@code text} against {@code root}, without mutating the tree. */
    static String resolveOne(ConfigurationNode root, String text, Map<String, String> variables) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(variables, "variables");
        return resolve(text, root, variables, new LinkedHashSet<>());
    }

    private static void visit(ConfigurationNode root, ConfigurationNode node, Map<String, String> variables) {
        if (node.isMap()) {
            node.childrenMap().values().forEach(child -> visit(root, child, variables));
        } else if (node.isList()) {
            node.childrenList().forEach(child -> visit(root, child, variables));
        } else if (node.rawScalar() instanceof String text
                && REFERENCE.matcher(text).find()) {
            setScalar(node, resolve(text, root, variables, new LinkedHashSet<>()));
        }
    }

    /** Expand every reference in {@code text}; {@code active} is the path stack guarding against cycles. */
    private static String resolve(
            String text, ConfigurationNode root, Map<String, String> variables, Set<String> active) {
        Matcher matcher = REFERENCE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String replacement = lookup(key, root, variables, active);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String lookup(
            String key, ConfigurationNode root, Map<String, String> variables, Set<String> active) {
        String provided = variables.get(key);
        if (provided != null) {
            return provided;
        }
        ConfigurationNode target = root.node((Object[]) key.split("\\."));
        if (target.virtual() || !(target.rawScalar() instanceof String value)) {
            return "${" + key + "}";
        }
        if (!active.add(key)) {
            throw new ConfigException("interpolation cycle through ${" + key + "} in config");
        }
        try {
            return resolve(value, root, variables, active);
        } finally {
            active.remove(key);
        }
    }

    private static void setScalar(ConfigurationNode node, String value) {
        try {
            node.set(value);
        } catch (SerializationException failure) {
            throw new ConfigException("failed to write interpolated value", failure);
        }
    }
}

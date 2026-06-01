package com.uxplima.uxmlib.command.annotation;

import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * Parses the {@code @}{@link Arg#def()} string of an omitted optional argument into the parameter's type.
 * Only the simple value types that make sense as a literal default are handled; a default on a richer type
 * (player, world) is rejected at parse time, since "the player named X" is not a sensible static default.
 */
final class Defaults {

    private Defaults() {}

    static Object parse(Class<?> type, String value) {
        if (type == String.class) {
            return value;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (type.isEnum()) {
            return parseEnum(type, value);
        }
        throw new CommandParseException("a default value is not supported for type " + type.getName());
    }

    @SuppressWarnings("unchecked") // guarded by type.isEnum()
    private static Object parseEnum(Class<?> type, String value) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new CommandParseException("unknown default '" + value + "' for enum " + type.getName());
    }
}

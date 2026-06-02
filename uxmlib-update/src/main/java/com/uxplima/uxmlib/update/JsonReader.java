package com.uxplima.uxmlib.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A small recursive-descent JSON reader over a single string. It builds a plain tree —
 * {@code Map<String,Object>} / {@code List<Object>} / {@code String} / {@code Double} / {@code Boolean} /
 * {@code null} — which is enough to pull a {@code tag_name} or {@code version_number} out of a releases
 * payload without pulling in a JSON dependency, and without the fragile substring-scan the competitor
 * libraries use. Not a general-purpose library: it rejects malformed or trailing input rather than guessing.
 */
final class JsonReader {

    // A sane cap on object/array nesting. A release payload is shallow; anything deeper is hostile or corrupt
    // and is rejected with an IllegalArgumentException (which the providers already catch) rather than left to
    // overflow the stack with a StackOverflowError that bypasses that catch.
    private static final int MAX_DEPTH = 64;

    private final String src;
    private int pos;
    private int depth;

    JsonReader(String src) {
        this.src = src;
    }

    @Nullable Object parse() {
        skipWhitespace();
        Object value = readValue();
        skipWhitespace();
        if (pos != src.length()) {
            throw error("trailing characters after JSON value");
        }
        return value;
    }

    private @Nullable Object readValue() {
        if (pos >= src.length()) {
            throw error("unexpected end of input");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw error("nesting too deep (max " + MAX_DEPTH + ")");
        }
    }

    private Map<String, Object> readObject() {
        enter();
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            depth--;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                depth--;
                return object;
            }
            if (c != ',') {
                throw error("expected ',' or '}' in object");
            }
        }
    }

    private List<Object> readArray() {
        enter();
        expect('[');
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            depth--;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                depth--;
                return array;
            }
            if (c != ',') {
                throw error("expected ',' or ']' in array");
            }
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw error("unterminated string");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                out.append(readEscape());
            } else {
                out.append(c);
            }
        }
    }

    private char readEscape() {
        if (pos >= src.length()) {
            throw error("unterminated escape");
        }
        char c = src.charAt(pos++);
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> readUnicodeEscape();
            default -> throw error("invalid escape: \\" + c);
        };
    }

    private char readUnicodeEscape() {
        if (pos + 4 > src.length()) {
            throw error("truncated unicode escape");
        }
        String hex = src.substring(pos, pos + 4);
        pos += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException notHex) {
            throw error("invalid unicode escape: \\u" + hex);
        }
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw error("invalid literal");
    }

    private @Nullable Object readNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw error("invalid literal");
    }

    private Double readNumber() {
        int start = pos;
        while (pos < src.length() && isNumberChar(src.charAt(pos))) {
            pos++;
        }
        if (pos == start) {
            String found = pos < src.length() ? "'" + src.charAt(pos) + "'" : "end of input";
            throw error("unexpected character " + found);
        }
        try {
            return Double.parseDouble(src.substring(start, pos));
        } catch (NumberFormatException notANumber) {
            throw error("invalid number: " + src.substring(start, pos));
        }
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
    }

    private void skipWhitespace() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw error("unexpected end of input");
        }
        return src.charAt(pos);
    }

    private char next() {
        if (pos >= src.length()) {
            throw error("unexpected end of input");
        }
        return src.charAt(pos++);
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw error("expected '" + expected + "' but found '" + c + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("malformed JSON at index " + pos + ": " + message);
    }
}

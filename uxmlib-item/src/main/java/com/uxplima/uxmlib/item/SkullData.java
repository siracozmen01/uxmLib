package com.uxplima.uxmlib.item;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * The owner of a player head, modelled as the three mutually exclusive ways to identify one: by account
 * {@link ByUuid uuid}, by {@link ByName name}, or by a raw base64 {@link ByTexture texture}. A sealed
 * type makes the choice explicit and lets {@link ItemBuilder} apply each variant with pattern matching,
 * so the three concerns can never be accidentally mixed.
 */
public sealed interface SkullData permits SkullData.ByUuid, SkullData.ByName, SkullData.ByTexture {

    /** A head owned by the account with this UUID. */
    record ByUuid(UUID uuid) implements SkullData {
        public ByUuid {
            Objects.requireNonNull(uuid, "uuid");
        }
    }

    /**
     * A head owned by the account with this name. Applying it resolves the name through
     * {@code Bukkit.getOfflinePlayer(String)}, which is a <strong>blocking</strong> lookup — prefer
     * {@link ByUuid} or {@link ByTexture} on the main thread, and use this only off-thread or for names
     * already cached by the server.
     */
    record ByName(String name) implements SkullData {
        public ByName {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    /** A head showing this base64-encoded texture value (the payload of a {@code textures} property). */
    record ByTexture(String base64) implements SkullData {
        public ByTexture {
            Objects.requireNonNull(base64, "base64");
            if (base64.isBlank()) {
                throw new IllegalArgumentException("base64 must not be blank");
            }
        }
    }

    /** A head owned by the account with {@code uuid}. */
    static SkullData ofUuid(UUID uuid) {
        return new ByUuid(uuid);
    }

    /** A head owned by the account named {@code name}. */
    static SkullData ofName(String name) {
        return new ByName(name);
    }

    /** A head showing the base64 {@code texture}. */
    static SkullData ofTexture(String texture) {
        return new ByTexture(texture);
    }

    /** A head showing the skin at {@code url}, wrapped into the base64 {@code textures} envelope Mojang uses. */
    static SkullData ofUrl(String url) {
        Objects.requireNonNull(url, "url");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return new ByTexture(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Route a single string to the right variant: a skin {@code http(s)} URL becomes a texture envelope,
     * a (dashed or undashed) UUID becomes {@link ByUuid}, a long base64 blob becomes {@link ByTexture},
     * and anything else is treated as a player {@link ByName name}. Lets config/menu authors paste any
     * skull form into one field.
     *
     * @throws IllegalArgumentException if {@code input} is blank
     */
    static SkullData parse(String input) {
        Objects.requireNonNull(input, "input");
        String value = input.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("skull value must not be blank");
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return ofUrl(value);
        }
        if (isDashedUuid(value)) {
            return ofUuid(UUID.fromString(value));
        }
        if (value.length() == 32 && isHex(value)) {
            return ofUuid(UUID.fromString(dash(value)));
        }
        if (isBase64Texture(value)) {
            return ofTexture(value);
        }
        return ofName(value);
    }

    private static boolean isDashedUuid(String value) {
        if (value.length() != 36) {
            return false;
        }
        for (int i = 0; i < 36; i++) {
            char c = value.charAt(i);
            boolean dashPosition = i == 8 || i == 13 || i == 18 || i == 23;
            if (dashPosition ? c != '-' : Character.digit(c, 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    // A texture value is a long run of base64 characters; a player name (max 16 chars) can never reach 60.
    private static boolean isBase64Texture(String value) {
        if (value.length() < 60) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean base64 = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '+'
                    || c == '/'
                    || c == '=';
            if (!base64) {
                return false;
            }
        }
        return true;
    }

    private static String dash(String undashed) {
        return undashed.substring(0, 8) + "-" + undashed.substring(8, 12) + "-" + undashed.substring(12, 16) + "-"
                + undashed.substring(16, 20) + "-" + undashed.substring(20);
    }
}

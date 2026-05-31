package com.uxplima.uxmlib.item;

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

    /** A head owned by the account with this name (resolved when applied). */
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
}

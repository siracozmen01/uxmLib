package com.uxplima.uxmlib.storage.sql;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure, stateless conversions between Java values and the compact forms a column likes to hold: an
 * {@link EnumSet} as an {@code int} bitmask, a {@link UUID} as an {@code int[4]} in Mojang order, and a
 * best-effort sniff of how a stored UUID was serialised. These are the building blocks a caller composes
 * into a {@link SqlType} (e.g. a flags column stored as one {@code INTEGER}); they touch no database and
 * have no state, so they are trivially testable and safe to share.
 *
 * <p>The {@code int[4]} layout matches Mojang's {@code UUIDUtil} — most-significant-bits high word first —
 * so a UUID written this way reads back identically to one Minecraft itself stored as an {@code int} array.
 */
public final class Codecs {

    // An int bitmask has 32 usable bits; an enum wider than that cannot be packed without losing a constant.
    private static final int MAX_BITMASK_CONSTANTS = 32;

    // The canonical hyphenated UUID text is exactly this long (8-4-4-4-12 plus four hyphens).
    private static final int UUID_TEXT_LENGTH = 36;

    // A UUID's binary form is exactly sixteen bytes (two 64-bit halves).
    private static final int UUID_BINARY_LENGTH = 16;

    private Codecs() {}

    /**
     * Pack {@code set} into an {@code int} bitmask, one bit per constant at its {@link Enum#ordinal()} (the
     * constant at ordinal 0 is bit {@code 1}, ordinal 1 is bit {@code 2}, and so on).
     *
     * @throws IllegalArgumentException if any contained constant has an ordinal {@code >= 32}
     */
    @SuppressWarnings("EnumOrdinal") // the ordinal IS the bit position — that is the bitmask contract
    public static <E extends Enum<E>> int enumSetToBitmask(EnumSet<E> set) {
        Objects.requireNonNull(set, "set");
        int mask = 0;
        for (E constant : set) {
            int ordinal = constant.ordinal();
            if (ordinal >= MAX_BITMASK_CONSTANTS) {
                throw new IllegalArgumentException(
                        "enum constant " + constant + " has ordinal " + ordinal + ", past the 32-bit mask");
            }
            mask |= 1 << ordinal;
        }
        return mask;
    }

    /**
     * Unpack {@code mask} back into an {@link EnumSet} of {@code type}: a constant is present when its
     * {@link Enum#ordinal()} bit is set. Bits with no matching constant are ignored, so a mask widened by a
     * future enum value (or a stray high bit) decodes cleanly rather than throwing.
     *
     * @throws IllegalArgumentException if {@code type} declares more than 32 constants (it cannot fit a mask)
     */
    @SuppressWarnings("EnumOrdinal") // the ordinal IS the bit position — that is the bitmask contract
    public static <E extends Enum<E>> EnumSet<E> bitmaskToEnumSet(int mask, Class<E> type) {
        Objects.requireNonNull(type, "type");
        E[] constants = type.getEnumConstants();
        if (constants.length > MAX_BITMASK_CONSTANTS) {
            throw new IllegalArgumentException(
                    type.getSimpleName() + " has " + constants.length + " constants, too many for a 32-bit mask");
        }
        EnumSet<E> set = EnumSet.noneOf(type);
        for (E constant : constants) {
            if ((mask & (1 << constant.ordinal())) != 0) {
                set.add(constant);
            }
        }
        return set;
    }

    /**
     * Split {@code uuid} into four ints in Mojang order: the high and low words of the most-significant bits
     * followed by the high and low words of the least-significant bits. The exact layout Minecraft uses for a
     * UUID stored as an {@code int[]}, so the result interoperates with NBT/DataFixerUpper data.
     */
    public static int[] uuidToIntArray(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    /**
     * Rebuild a {@link UUID} from four ints in Mojang order (the inverse of {@link #uuidToIntArray}).
     *
     * @throws IllegalArgumentException if {@code ints} is not exactly four elements long
     */
    public static UUID uuidFromIntArray(int[] ints) {
        Objects.requireNonNull(ints, "ints");
        if (ints.length != 4) {
            throw new IllegalArgumentException("a Mojang UUID int array has exactly 4 elements, got " + ints.length);
        }
        long most = ((long) ints[0] << 32) | (ints[1] & 0xFFFFFFFFL);
        long least = ((long) ints[2] << 32) | (ints[3] & 0xFFFFFFFFL);
        return new UUID(most, least);
    }

    /**
     * Best-effort guess at how a UUID column's raw bytes were serialised, so a reader can pick the right
     * decoder when a schema mixes forms (a {@code BINARY(16)} from one writer, a {@code VARCHAR(36)} from
     * another): {@link ByteForm#BINARY} for the 16-byte two-halves form, {@link ByteForm#TEXT} for the
     * canonical 36-character hyphenated string, {@link ByteForm#UNKNOWN} when neither fits.
     */
    public static ByteForm detectUuidBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == UUID_BINARY_LENGTH) {
            return ByteForm.BINARY;
        }
        if (bytes.length == UUID_TEXT_LENGTH && parsesAsUuidText(bytes)) {
            return ByteForm.TEXT;
        }
        return ByteForm.UNKNOWN;
    }

    private static boolean parsesAsUuidText(byte[] bytes) {
        try {
            UUID.fromString(new String(bytes, StandardCharsets.UTF_8));
            return true;
        } catch (IllegalArgumentException notAUuid) {
            return false;
        }
    }

    /** The serialised form {@link #detectUuidBytes} sniffed a UUID column's bytes into. */
    public enum ByteForm {
        /** Sixteen raw bytes: the two 64-bit halves, most-significant first. */
        BINARY,
        /** The canonical 36-character hyphenated UUID text, UTF-8 encoded. */
        TEXT,
        /** Neither recognised form — the caller decides how to handle it. */
        UNKNOWN
    }
}

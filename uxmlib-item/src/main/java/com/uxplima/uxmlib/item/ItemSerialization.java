package com.uxplima.uxmlib.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/**
 * Round-trips an {@link ItemStack} through Paper's native byte serialization — every component, NBT and
 * enchantment survives, unlike a name/lore-only copy. Use {@link #toBytes}/{@link #fromBytes} for binary
 * storage or {@link #toBase64}/{@link #fromBase64} for a config- or database-friendly string.
 *
 * <p>Each blob this writes carries a tiny self-describing header — four magic bytes, a one-byte format
 * version, then the server's data version — so a future reader can recognise the format, branch on the
 * version, and (later) migrate an older blob across Minecraft upgrades. Reading is back-compatible: a blob
 * written before the header existed (raw Paper bytes, no magic) still loads.
 *
 * <p>The format carries no length or checksum, so the legacy fall-back is a best-effort heuristic, not a
 * guarantee: a header-less blob is recognised purely by the absence of the {@code UXMI} magic. In the
 * astronomically unlikely event a raw Paper blob happens to begin with those exact four bytes it is treated
 * as headered; if its fifth byte is then not a format version this reader knows, the read fails fast rather
 * than silently mis-decoding. Treat the magic as a strong-but-not-absolute signal.
 */
public final class ItemSerialization {

    // "UXMI" — present at the very start of every blob this writer produces.
    private static final byte[] MAGIC = "UXMI".getBytes(StandardCharsets.US_ASCII);

    // Bumped only when the on-disk layout itself changes; a reader rejects a version it doesn't know.
    private static final byte FORMAT_VERSION = 1;

    // magic + format-version byte + 4-byte data version.
    private static final int HEADER_LENGTH = MAGIC.length + 1 + Integer.BYTES;

    private ItemSerialization() {}

    /** Serialize an item to bytes: a self-describing header followed by Paper's {@code serializeAsBytes}. */
    @SuppressWarnings("deprecation") // getUnsafe() is the only API exposing the server's data version to stamp
    public static byte[] toBytes(ItemStack item) {
        Objects.requireNonNull(item, "item");
        byte[] payload = item.serializeAsBytes();
        byte[] out = new byte[HEADER_LENGTH + payload.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[MAGIC.length] = FORMAT_VERSION;
        writeInt(out, MAGIC.length + 1, Bukkit.getUnsafe().getDataVersion());
        System.arraycopy(payload, 0, out, HEADER_LENGTH, payload.length);
        return out;
    }

    /**
     * Reconstruct an item from bytes produced by {@link #toBytes}. A legacy header-less blob (raw Paper
     * bytes, written before the header existed) is still accepted.
     *
     * @throws IllegalArgumentException if the bytes carry an unknown format version, or are not a valid
     *     serialized item
     */
    public static ItemStack fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return deserialize(headered(bytes) ? checkedPayload(bytes) : bytes);
    }

    /**
     * The Minecraft data version stamped into a header-bearing blob, or empty for a legacy header-less one.
     * Lets a caller decide whether a stored item predates the running server and may need migrating.
     */
    public static OptionalInt dataVersionOf(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (!headered(bytes)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(readInt(bytes, MAGIC.length + 1));
    }

    /** Serialize an item to a Base64 string (header included). */
    public static String toBase64(ItemStack item) {
        return Base64.getEncoder().encodeToString(toBytes(item));
    }

    /**
     * Reconstruct an item from a Base64 string produced by {@link #toBase64}; a legacy header-less string
     * still loads.
     *
     * @throws IllegalArgumentException if the string is not valid Base64 or not a serialized item
     */
    public static ItemStack fromBase64(String base64) {
        Objects.requireNonNull(base64, "base64");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("not valid Base64", invalid);
        }
        return fromBytes(bytes);
    }

    /**
     * Serialize an item to gzip-compressed bytes: the same headered blob {@link #toBytes} writes, run through
     * GZIP. Worth it for large items (shulker boxes, written books) or bulk storage where the NBT compresses
     * well; for a single trivial item the gzip framing can cost more than it saves.
     */
    public static byte[] toCompressedBytes(ItemStack item) {
        byte[] raw = toBytes(item);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(sink)) {
            gzip.write(raw);
        } catch (IOException impossible) {
            // A ByteArrayOutputStream never throws on write; rethrow unchecked to keep the API checked-free.
            throw new IllegalStateException("gzip of in-memory bytes failed", impossible);
        }
        return sink.toByteArray();
    }

    /**
     * Reconstruct an item from bytes produced by {@link #toCompressedBytes}.
     *
     * @throws IllegalArgumentException if the bytes are not valid gzip, or not a serialized item
     */
    public static ItemStack fromCompressedBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return fromBytes(gunzip(bytes));
    }

    /** Serialize an item to a gzip-compressed Base64 string (header included). */
    public static String toCompressedBase64(ItemStack item) {
        return Base64.getEncoder().encodeToString(toCompressedBytes(item));
    }

    /**
     * Reconstruct an item from a string produced by {@link #toCompressedBase64}.
     *
     * @throws IllegalArgumentException if the string is not valid Base64, not valid gzip, or not a serialized
     *     item
     */
    public static ItemStack fromCompressedBase64(String base64) {
        Objects.requireNonNull(base64, "base64");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("not valid Base64", invalid);
        }
        return fromCompressedBytes(bytes);
    }

    private static byte[] gunzip(byte[] bytes) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        } catch (IOException invalid) {
            throw new IllegalArgumentException("not valid gzip-compressed data", invalid);
        }
    }

    private static boolean headered(byte[] bytes) {
        return bytes.length >= HEADER_LENGTH && Arrays.equals(bytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
    }

    private static byte[] checkedPayload(byte[] bytes) {
        byte version = bytes[MAGIC.length];
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported item format version: " + version);
        }
        return Arrays.copyOfRange(bytes, HEADER_LENGTH, bytes.length);
    }

    private static ItemStack deserialize(byte[] payload) {
        try {
            return ItemStack.deserializeBytes(payload);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("not a valid serialized item", invalid);
        }
    }

    private static void writeInt(byte[] dst, int offset, int value) {
        dst[offset] = (byte) (value >>> 24);
        dst[offset + 1] = (byte) (value >>> 16);
        dst[offset + 2] = (byte) (value >>> 8);
        dst[offset + 3] = (byte) value;
    }

    private static int readInt(byte[] src, int offset) {
        return ((src[offset] & 0xFF) << 24)
                | ((src[offset + 1] & 0xFF) << 16)
                | ((src[offset + 2] & 0xFF) << 8)
                | (src[offset + 3] & 0xFF);
    }
}

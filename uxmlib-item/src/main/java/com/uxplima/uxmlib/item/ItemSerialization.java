package com.uxplima.uxmlib.item;

import java.util.Base64;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

/**
 * Round-trips an {@link ItemStack} through Paper's native byte serialization — every component, NBT and
 * enchantment survives, unlike a name/lore-only copy. Use {@link #toBytes}/{@link #fromBytes} for binary
 * storage or {@link #toBase64}/{@link #fromBase64} for a config- or database-friendly string.
 */
public final class ItemSerialization {

    private ItemSerialization() {}

    /** Serialize an item to bytes (Paper's {@code serializeAsBytes}). */
    public static byte[] toBytes(ItemStack item) {
        Objects.requireNonNull(item, "item");
        return item.serializeAsBytes();
    }

    /**
     * Reconstruct an item from bytes produced by {@link #toBytes}.
     *
     * @throws IllegalArgumentException if the bytes are not a valid serialized item
     */
    public static ItemStack fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            return ItemStack.deserializeBytes(bytes);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("not a valid serialized item", invalid);
        }
    }

    /** Serialize an item to a Base64 string. */
    public static String toBase64(ItemStack item) {
        return Base64.getEncoder().encodeToString(toBytes(item));
    }

    /**
     * Reconstruct an item from a Base64 string produced by {@link #toBase64}.
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
}

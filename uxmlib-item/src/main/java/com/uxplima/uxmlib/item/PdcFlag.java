package com.uxplima.uxmlib.item;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.papermc.paper.persistence.PersistentDataContainerView;

/**
 * Boolean flags over the {@link PersistentDataType#BYTE} convention that consuming plugins repeat everywhere:
 * a stored {@code (byte) 1} reads as {@code true}, a stored {@code (byte) 0} or an absent key as {@code false}.
 * It is a thin specialization of {@link Pdc} — the same key and {@code BYTE} type — so data written by the
 * hand-rolled idiom {@code pdc.set(key, BYTE, (byte) 1)} reads back unchanged through these helpers.
 *
 * <pre>{@code
 * Items.editPdc(item, pdc -> PdcFlag.set(pdc, key, true));
 * boolean soulbound = PdcFlag.get(item.getPersistentDataContainer(), key);
 * }</pre>
 */
public final class PdcFlag {

    private PdcFlag() {}

    /** The flag under {@code key}; an absent key reads as {@code false}. */
    public static boolean get(PersistentDataContainerView view, NamespacedKey key) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(key, "key");
        return Pdc.getOrDefault(view, key, PersistentDataType.BYTE, (byte) 0) != 0;
    }

    /** The flag under {@code key}, or {@code fallback} only when the key is absent; a present {@code 0} reads false. */
    public static boolean getOrDefault(PersistentDataContainerView view, NamespacedKey key, boolean fallback) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(key, "key");
        return Pdc.get(view, key, PersistentDataType.BYTE).map(b -> b != 0).orElse(fallback);
    }

    /** Store {@code value} under {@code key} as {@code (byte) 1} for {@code true} or {@code (byte) 0} for {@code false}. */
    public static void set(PersistentDataContainer pdc, NamespacedKey key, boolean value) {
        Objects.requireNonNull(pdc, "pdc");
        Objects.requireNonNull(key, "key");
        Pdc.set(pdc, key, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }

    /** Whether {@code key} holds a flag byte, regardless of whether it reads {@code true} or {@code false}. */
    public static boolean has(PersistentDataContainerView view, NamespacedKey key) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(key, "key");
        return Pdc.has(view, key, PersistentDataType.BYTE);
    }

    /** Remove the flag under {@code key}; a no-op when absent. */
    public static void remove(PersistentDataContainer pdc, NamespacedKey key) {
        Objects.requireNonNull(pdc, "pdc");
        Objects.requireNonNull(key, "key");
        pdc.remove(key);
    }
}

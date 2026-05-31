package com.uxplima.uxmlib.item;

import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

/**
 * Registry lookups for the types that lost their static constants in Paper 1.21. {@link Enchantment} and
 * {@link Attribute} are now registry entries, so {@code Enchantment.SHARPNESS} no longer compiles; these
 * helpers resolve them by key against the live registry instead.
 */
public final class Items {

    private Items() {}

    /** The enchantment for the vanilla key (e.g. {@code "sharpness"}); throws if none is registered. */
    public static Enchantment enchantment(String key) {
        Objects.requireNonNull(key, "key");
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .getOrThrow(NamespacedKey.minecraft(key));
    }

    /** The enchantment for the given key; throws if none is registered. */
    public static Enchantment enchantment(NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .getOrThrow(key);
    }

    /** The attribute for the vanilla key (e.g. {@code "attack_damage"}); throws if none is registered. */
    public static Attribute attribute(String key) {
        Objects.requireNonNull(key, "key");
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ATTRIBUTE)
                .getOrThrow(NamespacedKey.minecraft(key));
    }

    /** The attribute for the given key; throws if none is registered. */
    public static Attribute attribute(NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ATTRIBUTE)
                .getOrThrow(key);
    }
}

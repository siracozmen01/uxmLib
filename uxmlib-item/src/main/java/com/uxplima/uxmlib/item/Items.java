package com.uxplima.uxmlib.item;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

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

    /**
     * Edit {@code item}'s persistent data in one scoped pass: read the meta once, hand its
     * {@link PersistentDataContainer} to {@code editor}, then write the meta back. A no-op on an item with
     * no meta. Removes the read-meta / edit / set-meta dance (and the easy bug of forgetting to set it back).
     */
    public static void editPdc(ItemStack item, Consumer<PersistentDataContainer> editor) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(editor, "editor");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        editor.accept(meta.getPersistentDataContainer());
        item.setItemMeta(meta);
    }

    /**
     * Whether {@code a} and {@code b} carry the same value under a single persistent-data {@code key} — and
     * nothing else is compared. Unlike {@link ItemStack#isSimilar(ItemStack)}, material, name, lore and every
     * other key are ignored; this answers "are these the same logical item" when identity lives in one PDC tag
     * (a shop token, a custom-item id). Two items that both lack the key count as similar (both empty).
     */
    public static <P, C> boolean isSimilar(ItemStack a, ItemStack b, NamespacedKey key, PersistentDataType<P, C> type) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        return Objects.equals(pdcValue(a, key, type), pdcValue(b, key, type));
    }

    private static <P, C> @Nullable C pdcValue(ItemStack item, NamespacedKey key, PersistentDataType<P, C> type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key, type);
    }

    /**
     * Give {@code items} to {@code player}, dropping anything that does not fit at the player's feet so a
     * full inventory never silently swallows a reward.
     *
     * <p>This mutates the player's inventory and the world, so it <strong>must</strong> run on the region
     * thread that owns {@code player} (the main thread on Paper, the entity's region thread on Folia). It does
     * no scheduling of its own; call it from a region-correct context, or use
     * {@link #give(Scheduler, Player, ItemStack...)} from anywhere to hop there first.
     */
    public static void give(Player player, ItemStack... items) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(items, "items");
        for (ItemStack leftover : player.getInventory().addItem(items).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * Give {@code items} to {@code player} on the player's own region thread, hopping there via
     * {@code scheduler} first so the call is safe from any thread (including an async pool). The drop-on-full
     * behaviour matches {@link #give(Player, ItemStack...)}; the task is dropped if the player has logged off
     * by the time it runs.
     */
    public static void give(Scheduler scheduler, Player player, ItemStack... items) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(items, "items");
        ItemStack[] copy = items.clone();
        scheduler.entity(player, () -> give(player, copy));
    }
}

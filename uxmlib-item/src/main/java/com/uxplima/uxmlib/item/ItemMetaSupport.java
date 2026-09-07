package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.map.MapView;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

/**
 * The mutation bodies for {@link ItemBuilder}'s niche typed-meta setters, kept here as small
 * {@code (meta, value)} helpers so the builder itself stays a thin fluent surface. Each method assumes the
 * caller has already null-checked its arguments and routed it to the right meta type (via
 * {@code editTypedMeta}); these helpers only apply. Package-private on purpose: this is an implementation
 * seam of {@link ItemBuilder}, not part of the item API.
 */
final class ItemMetaSupport {

    private ItemMetaSupport() {}

    static void addLore(ItemMeta meta, Component line) {
        List<Component> current = meta.hasLore() ? meta.lore() : List.of();
        List<Component> next = new ArrayList<>(current != null ? current : List.of());
        next.add(line);
        meta.lore(next);
    }

    static void clearEnchants(ItemMeta meta) {
        for (Enchantment enchantment : List.copyOf(meta.getEnchants().keySet())) {
            meta.removeEnchant(enchantment);
        }
    }

    static void customModelDataFloats(ItemMeta meta, List<Float> floats) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.copyOf(floats));
        meta.setCustomModelDataComponent(component);
    }

    static void damage(ItemMeta meta, int damage) {
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damage);
        }
    }

    static void potionEffect(PotionMeta meta, PotionEffect effect) {
        meta.addCustomEffect(effect, true);
    }

    static void basePotionType(PotionMeta meta, PotionType type) {
        meta.setBasePotionType(type);
    }

    static void potionColor(PotionMeta meta, Color color) {
        meta.setColor(color);
    }

    static void fireworkEffect(FireworkMeta meta, FireworkEffect effect) {
        meta.addEffect(effect);
    }

    static void fireworkPower(FireworkMeta meta, int power) {
        meta.setPower(power);
    }

    static void armorTrim(ArmorMeta meta, ArmorTrim trim) {
        meta.setTrim(trim);
    }

    /**
     * Put a mob inside a spawner item.
     *
     * <p>The block state is asked for rather than tested for: a spawner item that nobody has edited answers
     * {@code false} to {@code hasBlockState()} and still hands back a usable {@code CreatureSpawner}, so a
     * guard on that flag would silently drop the mob on every item straight off the material.
     */
    static void spawnedType(BlockStateMeta meta, EntityType type) {
        if (meta.getBlockState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(type);
            meta.setBlockState(spawner);
        }
    }

    static void leatherColor(LeatherArmorMeta meta, Color color) {
        meta.setColor(color);
    }

    static void bookTitle(BookMeta meta, Component title) {
        meta.title(title);
    }

    static void bookAuthor(BookMeta meta, Component author) {
        meta.author(author);
    }

    static void bookPages(BookMeta meta, Component[] pages) {
        meta.addPages(pages);
    }

    static void storedEnchant(EnchantmentStorageMeta meta, Enchantment enchantment, int level) {
        meta.addStoredEnchant(enchantment, level, true);
    }

    static void bannerPattern(BannerMeta meta, DyeColor color, PatternType type) {
        meta.addPattern(new Pattern(color, type));
    }

    static void bannerPattern(BannerMeta meta, Pattern pattern) {
        meta.addPattern(pattern);
    }

    static void bannerPatterns(BannerMeta meta, List<Pattern> patterns) {
        meta.setPatterns(List.copyOf(patterns));
    }

    static void mapColor(MapMeta meta, Color color) {
        meta.setColor(color);
    }

    static void mapScaling(MapMeta meta, boolean scaling) {
        meta.setScaling(scaling);
    }

    static void mapView(MapMeta meta, MapView view) {
        meta.setMapView(view);
    }

    @SuppressWarnings("deprecation") // setLocationName is the only API for the on-hover map label; value still applies
    static void mapLocationName(MapMeta meta, String name) {
        meta.setLocationName(name);
    }

    static void skull(SkullMeta meta, SkullData skull) {
        switch (skull) {
            case SkullData.ByUuid byUuid -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(byUuid.uuid()));
            case SkullData.ByName byName -> meta.setOwningPlayer(offlinePlayerByName(byName.name()));
            case SkullData.ByTexture byTexture -> {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new ProfileProperty("textures", byTexture.base64()));
                meta.setPlayerProfile(profile);
            }
        }
    }

    @SuppressWarnings("deprecation") // name-based lookup is the only by-name option; documented on SkullData.ByName
    private static OfflinePlayer offlinePlayerByName(String name) {
        return Bukkit.getOfflinePlayer(name);
    }
}

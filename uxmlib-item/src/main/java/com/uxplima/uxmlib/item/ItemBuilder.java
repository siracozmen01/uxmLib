package com.uxplima.uxmlib.item;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

/**
 * Fluent builder for an {@link ItemStack}. Construction is mutable for ergonomics; {@link #build()}
 * applies everything onto the meta once and returns a fresh stack, so a builder can be reused to stamp
 * out variants. Text is always an Adventure {@link Component} — never a legacy colour string — and every
 * public method validates its inputs at entry.
 *
 * <p>Enchantments and attributes are passed as their resolved Paper objects; use {@link Items} to look
 * them up by key, since Paper 1.21 dropped the old static constants.
 */
public final class ItemBuilder {

    private final ItemStack stack;

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
    }

    /** A builder for a single item of {@code material} (which must not be {@link Material#AIR}). */
    public static ItemBuilder of(Material material) {
        Objects.requireNonNull(material, "material");
        if (material.isAir()) {
            throw new IllegalArgumentException("material must not be air");
        }
        return new ItemBuilder(new ItemStack(material));
    }

    /** A builder seeded from a defensive copy of {@code source}, carrying over its existing meta. */
    public static ItemBuilder from(ItemStack source) {
        Objects.requireNonNull(source, "source");
        return new ItemBuilder(new ItemStack(source));
    }

    /** Set the stack size, between 1 and the material's max stack size. */
    public ItemBuilder amount(int amount) {
        int max = stack.getMaxStackSize();
        if (amount < 1 || amount > max) {
            throw new IllegalArgumentException("amount must be 1.." + max);
        }
        stack.setAmount(amount);
        return this;
    }

    /** Set the display name. */
    public ItemBuilder name(Component name) {
        Objects.requireNonNull(name, "name");
        return editMeta(meta -> meta.displayName(name));
    }

    /** Replace the lore with these lines. */
    public ItemBuilder lore(Component... lines) {
        Objects.requireNonNull(lines, "lines");
        return lore(List.of(lines));
    }

    /** Replace the lore with these lines. */
    public ItemBuilder lore(List<Component> lines) {
        Objects.requireNonNull(lines, "lines");
        return editMeta(meta -> meta.lore(List.copyOf(lines)));
    }

    /** Append one line to the existing lore. */
    public ItemBuilder addLore(Component line) {
        Objects.requireNonNull(line, "line");
        return editMeta(meta -> {
            List<Component> current = meta.hasLore() ? meta.lore() : List.of();
            List<Component> next = new java.util.ArrayList<>(current != null ? current : List.of());
            next.add(line);
            meta.lore(next);
        });
    }

    /** Add an enchantment at {@code level} (level restrictions are ignored, so high levels are allowed). */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
        return editMeta(meta -> meta.addEnchant(enchantment, level, true));
    }

    /** Add display flags (e.g. {@link ItemFlag#HIDE_ENCHANTS}). */
    public ItemBuilder flags(ItemFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return editMeta(meta -> meta.addItemFlags(flags));
    }

    /** Set whether the item is unbreakable. */
    public ItemBuilder unbreakable(boolean unbreakable) {
        return editMeta(meta -> meta.setUnbreakable(unbreakable));
    }

    /**
     * Give the item the enchanted shimmer without an actual enchantment, via the native 1.21 glint
     * override — no dummy enchant, no need to hide it with a flag.
     */
    public ItemBuilder glow(boolean glow) {
        return editMeta(meta -> meta.setEnchantmentGlintOverride(glow));
    }

    /** Set the damage (durability used), for items that support it; a no-op on items that do not. */
    public ItemBuilder damage(int damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be >= 0");
        }
        return editMeta(meta -> {
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(damage);
            }
        });
    }

    /** Add an attribute modifier. Build the modifier with {@code new AttributeModifier(key, amount, op)}. */
    public ItemBuilder attribute(Attribute attribute, AttributeModifier modifier) {
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(modifier, "modifier");
        return editMeta(meta -> meta.addAttributeModifier(attribute, modifier));
    }

    /** Set the owner of a player head. Only valid when the material is {@link Material#PLAYER_HEAD}. */
    public ItemBuilder skull(SkullData skull) {
        Objects.requireNonNull(skull, "skull");
        if (stack.getType() != Material.PLAYER_HEAD) {
            throw new IllegalArgumentException("skull data is only valid for PLAYER_HEAD");
        }
        return editMeta(meta -> applySkull((SkullMeta) meta, skull));
    }

    /** Add a potion effect (only meaningful on a potion item); a no-op on items without potion meta. */
    public ItemBuilder potionEffect(PotionEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return editTypedMeta(PotionMeta.class, meta -> meta.addCustomEffect(effect, true));
    }

    /** Set the potion's display colour; a no-op on items without potion meta. */
    public ItemBuilder potionColor(Color color) {
        Objects.requireNonNull(color, "color");
        return editTypedMeta(PotionMeta.class, meta -> meta.setColor(color));
    }

    /** Add a firework effect; a no-op on items without firework meta. */
    public ItemBuilder fireworkEffect(FireworkEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return editTypedMeta(FireworkMeta.class, meta -> meta.addEffect(effect));
    }

    /** Set the firework flight power (0–127); a no-op on items without firework meta. */
    public ItemBuilder fireworkPower(int power) {
        if (power < 0 || power > 127) {
            throw new IllegalArgumentException("power must be 0..127");
        }
        return editTypedMeta(FireworkMeta.class, meta -> meta.setPower(power));
    }

    /** Dye leather armour; a no-op on items without leather-armour meta. */
    public ItemBuilder leatherColor(Color color) {
        Objects.requireNonNull(color, "color");
        return editTypedMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
    }

    /** Set a written book's title; a no-op on items without book meta. */
    public ItemBuilder bookTitle(Component title) {
        Objects.requireNonNull(title, "title");
        return editTypedMeta(BookMeta.class, meta -> meta.title(title));
    }

    /** Set a written book's author; a no-op on items without book meta. */
    public ItemBuilder bookAuthor(Component author) {
        Objects.requireNonNull(author, "author");
        return editTypedMeta(BookMeta.class, meta -> meta.author(author));
    }

    /** Append pages to a written book; a no-op on items without book meta. */
    public ItemBuilder bookPages(Component... pages) {
        Objects.requireNonNull(pages, "pages");
        return editTypedMeta(BookMeta.class, meta -> meta.addPages(pages));
    }

    /** Add a stored enchantment (for an enchanted book); a no-op on items without that meta. */
    public ItemBuilder storedEnchant(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
        return editTypedMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, level, true));
    }

    /**
     * Mutate the raw {@link ItemMeta} directly; the result is written back to the stack. A no-op if the
     * item has no meta (only {@link Material#AIR}, which {@link #of} already rejects).
     */
    public ItemBuilder editMeta(Consumer<ItemMeta> editor) {
        Objects.requireNonNull(editor, "editor");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return this;
        }
        editor.accept(meta);
        stack.setItemMeta(meta);
        return this;
    }

    /** Mutate the meta only if it is of {@code metaType}; otherwise this is a no-op. */
    public <M extends ItemMeta> ItemBuilder editTypedMeta(Class<M> metaType, Consumer<M> editor) {
        Objects.requireNonNull(metaType, "metaType");
        Objects.requireNonNull(editor, "editor");
        return editMeta(meta -> {
            if (metaType.isInstance(meta)) {
                editor.accept(metaType.cast(meta));
            }
        });
    }

    /** Mutate the item's {@link PersistentDataContainer}; the result is written back to the stack. */
    public ItemBuilder editPersistentData(Consumer<PersistentDataContainer> editor) {
        Objects.requireNonNull(editor, "editor");
        return editMeta(meta -> editor.accept(meta.getPersistentDataContainer()));
    }

    /** Build a fresh, independent {@link ItemStack} with everything applied. */
    public ItemStack build() {
        return new ItemStack(stack);
    }

    private static void applySkull(SkullMeta meta, SkullData skull) {
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
    private static org.bukkit.OfflinePlayer offlinePlayerByName(String name) {
        return Bukkit.getOfflinePlayer(name);
    }
}

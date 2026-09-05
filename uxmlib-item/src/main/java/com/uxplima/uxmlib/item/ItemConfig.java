package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Reads an item spec from a HOCON {@link ConfigurationNode} into an {@link ItemBuilder}: the single
 * most-replicated competitor pattern, and the seam the GUI menu loader sits on. A node looks like:
 *
 * <pre>{@code
 * material = DIAMOND_SWORD            # required
 * name = "<red>Blade"                 # MiniMessage
 * lore = ["<gray>line one", "two"]    # MiniMessage; a "\n" splits one entry into several lines
 * amount = 1
 * enchants { sharpness = 5 }          # vanilla key -> level
 * flags = [HIDE_ENCHANTS]
 * custom-model-data = 42
 * unbreakable = true
 * glow = true
 * hide-vanilla-tooltip = true         # silence the lines the client writes under the lore
 * skull = "Notch"                     # only for PLAYER_HEAD; routed through SkullData.parse
 * }</pre>
 *
 * <p>Name and lore pass through MiniMessage with any supplied {@link TagResolver placeholders}; lore can be
 * auto-wrapped to a width. One-way for now (config to item); a writer can come later. Lives in the item
 * module on purpose: it has no GUI dependency.
 */
public final class ItemConfig {

    private ItemConfig() {}

    /** Load the spec at {@code node} into a builder, with no placeholders and no lore wrapping. */
    public static ItemBuilder load(ConfigurationNode node) {
        return load(node, List.of(), 0);
    }

    /** Load the spec, resolving {@code resolvers} in the name and lore MiniMessage. */
    public static ItemBuilder load(ConfigurationNode node, List<TagResolver> resolvers) {
        return load(node, resolvers, 0);
    }

    /**
     * Load the spec, resolving {@code resolvers} and (when {@code wrapWidth > 0}) word-wrapping each lore
     * line to that visible width.
     *
     * @throws IllegalArgumentException if {@code material} is missing/unknown or any field is malformed
     */
    public static ItemBuilder load(ConfigurationNode node, List<TagResolver> resolvers, int wrapWidth) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(resolvers, "resolvers");
        if (wrapWidth < 0) {
            throw new IllegalArgumentException("wrapWidth must be >= 0");
        }
        TagResolver[] tags = resolvers.toArray(TagResolver[]::new);
        ItemBuilder builder = ItemBuilder.of(material(node));
        applyAmount(node, builder);
        applyName(node, builder, tags);
        applyLore(node, builder, tags, wrapWidth);
        applyEnchants(node, builder);
        applyFlags(node, builder);
        applyScalars(node, builder);
        applySkull(node, builder);
        return builder;
    }

    private static Material material(ConfigurationNode node) {
        String raw = node.node("material").getString();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("item config requires a 'material'");
        }
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw new IllegalArgumentException("unknown material: " + raw);
        }
        return material;
    }

    private static void applyAmount(ConfigurationNode node, ItemBuilder builder) {
        int amount = node.node("amount").getInt(1);
        if (amount != 1) {
            builder.amount(amount);
        }
    }

    private static void applyName(ConfigurationNode node, ItemBuilder builder, TagResolver[] tags) {
        String name = node.node("name").getString();
        if (name != null && !name.isEmpty()) {
            builder.name(Text.mini(name, tags));
        }
    }

    private static void applyLore(ConfigurationNode node, ItemBuilder builder, TagResolver[] tags, int wrapWidth) {
        List<String> raw = stringList(node.node("lore"), "lore");
        if (raw.isEmpty()) {
            return;
        }
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        for (String entry : raw) {
            for (String wrapped : wrapWidth > 0
                    ? LoreWrap.wrap(entry, wrapWidth)
                    : entry.lines().toList()) {
                lines.add(Text.mini(wrapped, tags));
            }
        }
        builder.lore(lines);
    }

    private static void applyEnchants(ConfigurationNode node, ItemBuilder builder) {
        for (var entry : node.node("enchants").childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            int level = entry.getValue().getInt(-1);
            if (level < 1) {
                throw new IllegalArgumentException("enchant '" + key + "' needs a level >= 1");
            }
            builder.enchant(enchantment(key), level);
        }
    }

    private static org.bukkit.enchantments.Enchantment enchantment(String key) {
        try {
            return Items.enchantment(key.toLowerCase(Locale.ROOT));
        } catch (RuntimeException unknown) {
            throw new IllegalArgumentException("unknown enchant: " + key, unknown);
        }
    }

    private static void applyFlags(ConfigurationNode node, ItemBuilder builder) {
        List<String> raw = stringList(node.node("flags"), "flags");
        if (raw.isEmpty()) {
            return;
        }
        List<ItemFlag> flags = new ArrayList<>(raw.size());
        for (String name : raw) {
            flags.add(parseFlag(name));
        }
        builder.flags(flags.toArray(ItemFlag[]::new));
    }

    private static ItemFlag parseFlag(String name) {
        try {
            return ItemFlag.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown item flag: " + name, unknown);
        }
    }

    private static void applyScalars(ConfigurationNode node, ItemBuilder builder) {
        ConfigurationNode cmd = node.node("custom-model-data");
        if (!cmd.virtual()) {
            builder.customModelData(cmd.getInt());
        }
        if (node.node("unbreakable").getBoolean(false)) {
            builder.unbreakable(true);
        }
        if (node.node("glow").getBoolean(false)) {
            builder.glow(true);
        }
        if (node.node("hide-vanilla-tooltip").getBoolean(false)) {
            builder.vanillaTooltip(false);
        }
    }

    private static void applySkull(ConfigurationNode node, ItemBuilder builder) {
        String skull = node.node("skull").getString();
        if (skull != null && !skull.isBlank()) {
            builder.skull(SkullData.parse(skull));
        }
    }

    private static List<String> stringList(ConfigurationNode node, String where) {
        try {
            List<String> value = node.getList(String.class);
            return value == null ? List.of() : value;
        } catch (SerializationException malformed) {
            throw new IllegalArgumentException("'" + where + "' must be a list of strings", malformed);
        }
    }
}

package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The typed-meta half of {@link ItemConfig}: the six blocks that only some items have, and that the reader
 * carried none of until a shop tried to price a potion.
 *
 * <p>The token grammar is the one {@code uxmlib-menu} already gives an operator in a menu's {@code decor}
 * block, key for key, so a block that draws an item in a menu file can be pasted into an item file and mean
 * the same thing. Writing a second spelling here would have left the library with two.
 *
 * <p>Where this and the menu renderer part company is what a bad value does. The renderer draws a menu many
 * times a second and skips a token it cannot resolve, because a menu with one wrong tile still opens. This is
 * read once, at load, into an item an operator will sell or hand out, so a mistyped potion name throws and
 * names itself instead of quietly producing a water bottle.
 *
 * <p>Package-private: an implementation seam of {@link ItemConfig}, not part of the item API.
 */
final class ItemConfigMeta {

    /** How long a custom potion effect lasts when the token does not say: fifteen seconds. */
    private static final int DEFAULT_EFFECT_TICKS = 300;

    private ItemConfigMeta() {}

    /** Read every typed-meta block the node carries and apply it to {@code builder}. */
    static void apply(ConfigurationNode node, ItemBuilder builder) {
        applyPotion(node.node("potion"), builder);
        applyLeatherColor(node.node("leather-color"), builder);
        applyFirework(node.node("firework"), builder);
        applyTrim(node.node("trim"), builder);
        applyBanner(node.node("banner").node("patterns"), builder);
        applySpawner(node.node("spawner"), builder);
    }

    /** {@code potion { type = strength, color = "#00AAFF", effects = ["speed:1:600"] }}. */
    private static void applyPotion(ConfigurationNode potion, ItemBuilder builder) {
        if (absent(potion)) {
            return;
        }
        String type = text(potion.node("type"));
        if (type != null) {
            builder.basePotionType(fromRegistry(RegistryKey.POTION, type, "potion type"));
        }
        String color = text(potion.node("color"));
        if (color != null) {
            builder.potionColor(color(color, "potion.color"));
        }
        for (String token : ItemConfig.stringList(potion.node("effects"), "potion.effects")) {
            builder.potionEffect(potionEffect(token));
        }
    }

    /** One {@code effect:amplifier:durationTicks} token; the amplifier and the duration are optional. */
    private static PotionEffect potionEffect(String token) {
        String[] parts = token.split(":", 3);
        PotionEffectType type = fromRegistry(RegistryKey.MOB_EFFECT, parts[0], "potion effect");
        int amplifier = parts.length > 1 ? number(parts[1], "potion effect amplifier") : 0;
        int duration = parts.length > 2 ? number(parts[2], "potion effect duration") : DEFAULT_EFFECT_TICKS;
        if (amplifier < 0) {
            throw new IllegalArgumentException("a potion effect amplifier must be >= 0: " + token);
        }
        return new PotionEffect(type, duration, amplifier);
    }

    /** {@code leather-color = "#A1FF33"}, which dyes leather armour. */
    private static void applyLeatherColor(ConfigurationNode node, ItemBuilder builder) {
        String raw = text(node);
        if (raw != null) {
            builder.leatherColor(color(raw, "leather-color"));
        }
    }

    /** {@code firework { power = 2, effects = ["ball_large:#ff0000,#ffff00:#ffffff:flicker,trail"] }}. */
    private static void applyFirework(ConfigurationNode firework, ItemBuilder builder) {
        if (absent(firework)) {
            return;
        }
        ConfigurationNode power = firework.node("power");
        if (!absent(power)) {
            builder.fireworkPower(power.getInt());
        }
        for (String token : ItemConfig.stringList(firework.node("effects"), "firework.effects")) {
            builder.fireworkEffect(fireworkEffect(token));
        }
    }

    /**
     * One {@code type:colours:fade-colours:flags} token. The last two sections are optional; the flags are
     * {@code flicker} and {@code trail}. Colours inside a section are separated by a comma, so a colour there
     * is a {@code #RRGGBB} hex or a dye name and never an {@code r,g,b} triple.
     */
    private static FireworkEffect fireworkEffect(String token) {
        String[] parts = token.split(":", -1);
        FireworkEffect.Builder effect = FireworkEffect.builder().with(fireworkType(parts[0]));
        List<Color> colors = parts.length > 1 ? colorList(parts[1], "firework colour") : List.of();
        if (colors.isEmpty()) {
            throw new IllegalArgumentException("a firework effect needs at least one colour: " + token);
        }
        colors.forEach(effect::withColor);
        if (parts.length > 2) {
            colorList(parts[2], "firework fade colour").forEach(effect::withFade);
        }
        if (parts.length > 3) {
            applyFireworkFlags(effect, parts[3], token);
        }
        return effect.build();
    }

    private static void applyFireworkFlags(FireworkEffect.Builder effect, String flags, String token) {
        for (String flag : flags.split(",", -1)) {
            String name = flag.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            switch (name) {
                case "flicker" -> effect.flicker(true);
                case "trail" -> effect.trail(true);
                default ->
                    throw new IllegalArgumentException(
                            "unknown firework flag '" + flag + "' in " + token + "; write flicker or trail");
            }
        }
    }

    private static FireworkEffect.Type fireworkType(String raw) {
        try {
            return FireworkEffect.Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown firework type: " + raw, unknown);
        }
    }

    /** {@code trim { material = diamond, pattern = sentry }}. */
    private static void applyTrim(ConfigurationNode trim, ItemBuilder builder) {
        if (absent(trim)) {
            return;
        }
        String material = text(trim.node("material"));
        String pattern = text(trim.node("pattern"));
        if (material == null || pattern == null) {
            throw new IllegalArgumentException("'trim' needs both a material and a pattern");
        }
        TrimMaterial resolved = fromRegistry(RegistryKey.TRIM_MATERIAL, material, "trim material");
        TrimPattern shape = fromRegistry(RegistryKey.TRIM_PATTERN, pattern, "trim pattern");
        builder.armorTrim(new ArmorTrim(resolved, shape));
    }

    /**
     * {@code banner { patterns = ["stripe_top:red", "border:white"] }}, laid on in the order written. A shield
     * carries banner meta too, so the same block decorates one.
     */
    private static void applyBanner(ConfigurationNode node, ItemBuilder builder) {
        List<String> tokens = ItemConfig.stringList(node, "banner.patterns");
        if (tokens.isEmpty()) {
            return;
        }
        List<Pattern> patterns = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            patterns.add(pattern(token));
        }
        builder.bannerPatterns(patterns);
    }

    /** One {@code pattern:dyecolor} token. */
    private static Pattern pattern(String token) {
        int colon = token.lastIndexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("a banner pattern is written 'pattern:dyecolor', not: " + token);
        }
        PatternType type = fromRegistry(RegistryKey.BANNER_PATTERN, token.substring(0, colon), "banner pattern");
        return new Pattern(dye(token.substring(colon + 1)), type);
    }

    /** {@code spawner = zombie}, the mob a spawner item spawns once it is placed. */
    private static void applySpawner(ConfigurationNode node, ItemBuilder builder) {
        String raw = text(node);
        if (raw != null) {
            builder.spawnedType(fromRegistry(RegistryKey.ENTITY_TYPE, raw, "spawner mob"));
        }
    }

    /** Every colour in a comma-separated section, in the order written. */
    private static List<Color> colorList(String section, String what) {
        List<Color> colors = new ArrayList<>();
        for (String part : section.split(",", -1)) {
            if (!part.isBlank()) {
                colors.add(color(part, what));
            }
        }
        return colors;
    }

    /** A {@code #RRGGBB} hex, an {@code r,g,b} triple, or a dye name. */
    private static Color color(String raw, String where) {
        String value = raw.trim();
        try {
            if (value.startsWith("#")) {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            }
            if (value.contains(",")) {
                String[] parts = value.split(",", -1);
                if (parts.length != 3) {
                    throw new IllegalArgumentException("an r,g,b colour needs three channels");
                }
                return Color.fromRGB(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()));
            }
            return dye(value).getColor();
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("'" + where + "' is not a colour: " + raw, malformed);
        }
    }

    private static DyeColor dye(String name) {
        try {
            return DyeColor.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown dye colour: " + name, unknown);
        }
    }

    private static int number(String raw, String what) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(what + " must be a whole number, was: " + raw, notANumber);
        }
    }

    /** Resolve a registry id, with or without a namespace. An id nobody registered is an error, not a skip. */
    private static <T extends Keyed> T fromRegistry(RegistryKey<T> key, String raw, String what) {
        @Nullable NamespacedKey named = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        @Nullable T value = named == null
                ? null
                : RegistryAccess.registryAccess().getRegistry(key).get(named);
        if (value == null) {
            throw new IllegalArgumentException("unknown " + what + ": " + raw);
        }
        return value;
    }

    /** A node's trimmed value, or {@code null} when the node is absent, null or blank. */
    private static @Nullable String text(ConfigurationNode node) {
        if (absent(node)) {
            return null;
        }
        String value = node.getString("");
        return value.isBlank() ? null : value.trim();
    }

    private static boolean absent(ConfigurationNode node) {
        return node.virtual() || node.isNull();
    }
}

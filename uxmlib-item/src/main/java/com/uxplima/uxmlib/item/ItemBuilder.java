package com.uxplima.uxmlib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import io.papermc.paper.datacomponent.DataComponentType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Fluent builder for an {@link ItemStack}. Construction is mutable for ergonomics; {@link #build()}
 * applies everything onto the meta once and returns a fresh stack, so a builder can be reused to stamp
 * out variants. Text is always an Adventure {@link Component} (never a legacy colour string), and every
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

    /**
     * Set the display name. Also pins the item's rarity to {@link ItemRarity#COMMON} so the name renders in
     * the colour the component carries rather than the material's rarity tint: the client only paints a
     * display name in the rarity colour when the name component has no colour of its own, and a custom name
     * whose root run is uncoloured (e.g. a plain-text segment before a coloured one) would otherwise pick up
     * the light-purple epic/rare tint of materials like a nether star or totem.
     */
    public ItemBuilder name(Component name) {
        Objects.requireNonNull(name, "name");
        return editMeta(meta -> {
            meta.displayName(nonItalic(name));
            meta.setRarity(ItemRarity.COMMON);
        });
    }

    /**
     * Item display names and lore inherit the vanilla client's default italic styling unless the component
     * explicitly sets ITALIC. We default it to off (only when the caller left it unset) so names/lore render
     * upright; a caller that wants italic still gets it by setting ITALIC=true on their component.
     */
    private static Component nonItalic(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Split a lore component into one component per visual line. Minecraft renders item lore as a list of
     * components (one entry per line), and does <em>not</em> break a single entry on an embedded newline; a
     * lore entry that contains {@code \n} (e.g. a MiniMessage string with {@code <newline>}) shows the newline
     * as a stray control glyph instead of wrapping. So we expand a newline-bearing component into separate
     * lines here, preserving each run's colour and decorations, and default each line to non-italic. A component
     * with no embedded newline is returned untouched (only the italic default applied), so existing single-line
     * lore is unaffected.
     */
    private static List<Component> loreLines(Component line) {
        if (!containsNewline(line)) {
            return List.of(nonItalic(line));
        }
        List<List<Component>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        flattenInto(line, Style.empty(), lines);
        List<Component> out = new ArrayList<>(lines.size());
        for (List<Component> segments : lines) {
            out.add(nonItalic(Component.join(JoinConfiguration.noSeparators(), segments)));
        }
        return out;
    }

    private static boolean containsNewline(Component component) {
        if (component instanceof TextComponent text && text.content().indexOf('\n') >= 0) {
            return true;
        }
        for (Component child : component.children()) {
            if (containsNewline(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Depth-first walk that accumulates flat, fully-styled text runs into {@code lines}, starting a new line on
     * each {@code \n}. Each node's style is resolved against its inherited style so a run keeps the colour and
     * decorations it rendered with before the split; children inherit the resolved style, mirroring how the
     * client paints a component tree.
     */
    private static void flattenInto(Component component, Style inherited, List<List<Component>> lines) {
        Style resolved = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text) {
            String[] parts = text.content().split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(new ArrayList<>());
                }
                if (!parts[i].isEmpty()) {
                    lines.get(lines.size() - 1).add(Component.text(parts[i]).style(resolved));
                }
            }
        } else {
            lines.get(lines.size() - 1).add(component.children(List.of()).style(resolved));
        }
        for (Component child : component.children()) {
            flattenInto(child, resolved, lines);
        }
    }

    /** Remove the display name, restoring the item's default (vanilla) name. */
    public ItemBuilder clearName() {
        return editMeta(meta -> meta.displayName(null));
    }

    /** Replace the lore with these lines. */
    public ItemBuilder lore(Component... lines) {
        Objects.requireNonNull(lines, "lines");
        return lore(List.of(lines));
    }

    /** Replace the lore with these lines. */
    public ItemBuilder lore(List<Component> lines) {
        Objects.requireNonNull(lines, "lines");
        List<Component> expanded = new ArrayList<>(lines.size());
        for (Component line : lines) {
            expanded.addAll(loreLines(line));
        }
        return editMeta(meta -> meta.lore(expanded));
    }

    /** Remove all lore lines. */
    public ItemBuilder clearLore() {
        return editMeta(meta -> meta.lore(null));
    }

    /** Append one line to the existing lore. */
    public ItemBuilder addLore(Component line) {
        Objects.requireNonNull(line, "line");
        return editMeta(meta -> {
            for (Component piece : loreLines(line)) {
                ItemMetaSupport.addLore(meta, piece);
            }
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

    /** Remove one enchantment, if present. */
    public ItemBuilder removeEnchant(Enchantment enchantment) {
        Objects.requireNonNull(enchantment, "enchantment");
        return editMeta(meta -> meta.removeEnchant(enchantment));
    }

    /** Remove every enchantment. */
    public ItemBuilder clearEnchants() {
        return editMeta(ItemMetaSupport::clearEnchants);
    }

    /** Add display flags (e.g. {@link ItemFlag#HIDE_ENCHANTS}). */
    public ItemBuilder flags(ItemFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return editMeta(meta -> meta.addItemFlags(flags));
    }

    /** Remove display flags, restoring their default visibility. */
    public ItemBuilder removeFlags(ItemFlag... flags) {
        Objects.requireNonNull(flags, "flags");
        return editMeta(meta -> meta.removeItemFlags(flags));
    }

    /** Set whether the item is unbreakable. */
    public ItemBuilder unbreakable(boolean unbreakable) {
        return editMeta(meta -> meta.setUnbreakable(unbreakable));
    }

    /**
     * Give the item the enchanted shimmer without an actual enchantment, via the native 1.21 glint
     * override: no dummy enchant, no need to hide it with a flag.
     */
    public ItemBuilder glow(boolean glow) {
        return editMeta(meta -> meta.setEnchantmentGlintOverride(glow));
    }

    /** Set the custom model data resource packs use to select a model variant. */
    @SuppressWarnings("deprecation") // the int custom-model-data accessor; still the common resource-pack key
    public ItemBuilder customModelData(int data) {
        return editMeta(meta -> meta.setCustomModelData(data));
    }

    /**
     * Set the full 1.21 custom-model-data component (floats, flags, strings and colours), replacing any
     * existing one. Use this over {@link #customModelData(int)} for the richer resource-pack selectors a
     * 1.21.4+ pack can key off.
     */
    public ItemBuilder customModelData(CustomModelDataComponent component) {
        Objects.requireNonNull(component, "component");
        return editMeta(meta -> meta.setCustomModelDataComponent(component));
    }

    /**
     * Set only the float list on the custom-model-data component, leaving its other fields as they are. This
     * is the common case: a resource pack selecting a model variant by a numeric range.
     */
    public ItemBuilder customModelDataFloats(List<Float> floats) {
        Objects.requireNonNull(floats, "floats");
        return editMeta(meta -> ItemMetaSupport.customModelDataFloats(meta, floats));
    }

    /** Override the item model with a resource-pack model key (the native 1.21 {@code item_model}). */
    public ItemBuilder itemModel(NamespacedKey model) {
        Objects.requireNonNull(model, "model");
        return editMeta(meta -> meta.setItemModel(model));
    }

    /** Override the tooltip background/frame with a resource-pack {@code tooltip_style} key. */
    public ItemBuilder tooltipStyle(NamespacedKey style) {
        Objects.requireNonNull(style, "style");
        return editMeta(meta -> meta.setTooltipStyle(style));
    }

    /** Hide the item's tooltip entirely on hover, leaving only the icon. */
    public ItemBuilder hideTooltip(boolean hide) {
        return editMeta(meta -> meta.setHideTooltip(hide));
    }

    /**
     * Whether the client may write its own lines under this item's lore. Off, it hides
     * {@link Tooltips#VANILLA_COMPONENTS}, which is what a menu icon wants: a leather chestplate used as a
     * button otherwise says "Dyed" and "Armor" underneath whatever the menu wrote, and the client has no
     * way of knowing it is looking at a button.
     *
     * <p>This is about drawing and not about behaviour. Hiding {@code EQUIPPABLE} removes the line, not the
     * equip; an item that can be worn can still be worn. A menu stops that by cancelling the click, which
     * is where it belongs.
     *
     * <p>Not the same as {@link #hideTooltip(boolean)}, which takes the whole tooltip away, name and lore
     * included. This one keeps everything the plugin wrote and silences only what the client adds. The two
     * compose: the whole-tooltip flag survives a call to either of these.
     */
    public ItemBuilder vanillaTooltip(boolean shown) {
        return hiddenComponents(shown ? Set.of() : Tooltips.VANILLA_COMPONENTS);
    }

    /**
     * Hide exactly {@code hidden} and nothing else, replacing whatever was hidden before. For the tile that
     * wants the standard silence with one line back (the trim on a cosmetic armour piece, say) build the
     * set from {@link Tooltips#VANILLA_COMPONENTS} rather than typing it out, so a component Minecraft adds
     * later arrives hidden with the next release instead of being forgotten.
     */
    public ItemBuilder hiddenComponents(Set<DataComponentType> hidden) {
        Objects.requireNonNull(hidden, "hidden");
        Tooltips.hide(stack, hidden);
        return this;
    }

    /**
     * Override how enchantable the item is (its enchanting-table weight); higher means richer enchantments
     * for the same level cost. Must be positive: Minecraft treats the value as an enchantability rating.
     */
    public ItemBuilder enchantable(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("enchantable must be >= 1");
        }
        return editMeta(meta -> meta.setEnchantable(value));
    }

    /**
     * Re-type the in-progress item to {@code material}, carrying over the existing meta where the new type
     * still supports it. {@code material} must not be {@link Material#AIR}, matching {@link #of(Material)}.
     */
    @SuppressWarnings("deprecation") // setType re-types the in-progress stack in place; that is the intent here
    public ItemBuilder material(Material material) {
        Objects.requireNonNull(material, "material");
        if (material.isAir()) {
            throw new IllegalArgumentException("material must not be air");
        }
        stack.setType(material);
        return this;
    }

    /** Cap how many of this item stack together (1..99), overriding the material default. */
    public ItemBuilder maxStackSize(int max) {
        if (max < 1 || max > 99) {
            throw new IllegalArgumentException("maxStackSize must be 1..99");
        }
        return editMeta(meta -> meta.setMaxStackSize(max));
    }

    /** Set the item's rarity, which tints the display name colour. */
    public ItemBuilder rarity(ItemRarity rarity) {
        Objects.requireNonNull(rarity, "rarity");
        return editMeta(meta -> meta.setRarity(rarity));
    }

    /** Set the damage (durability used), for items that support it; a no-op on items that do not. */
    public ItemBuilder damage(int damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be >= 0");
        }
        return editMeta(meta -> ItemMetaSupport.damage(meta, damage));
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
        return editTypedMeta(SkullMeta.class, meta -> ItemMetaSupport.skull(meta, skull));
    }

    /** Add a potion effect (only meaningful on a potion item); a no-op on items without potion meta. */
    public ItemBuilder potionEffect(PotionEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return editTypedMeta(PotionMeta.class, meta -> ItemMetaSupport.potionEffect(meta, effect));
    }

    /**
     * Set the potion's base type, which is what names it and what a brewing stand made; a no-op on items
     * without potion meta. The custom effects of {@link #potionEffect} sit on top of it.
     */
    public ItemBuilder basePotionType(PotionType type) {
        Objects.requireNonNull(type, "type");
        return editTypedMeta(PotionMeta.class, meta -> ItemMetaSupport.basePotionType(meta, type));
    }

    /** Set the potion's display colour; a no-op on items without potion meta. */
    public ItemBuilder potionColor(Color color) {
        Objects.requireNonNull(color, "color");
        return editTypedMeta(PotionMeta.class, meta -> ItemMetaSupport.potionColor(meta, color));
    }

    /** Add a firework effect; a no-op on items without firework meta. */
    public ItemBuilder fireworkEffect(FireworkEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return editTypedMeta(FireworkMeta.class, meta -> ItemMetaSupport.fireworkEffect(meta, effect));
    }

    /** Set the firework flight power (0 to 127); a no-op on items without firework meta. */
    public ItemBuilder fireworkPower(int power) {
        if (power < 0 || power > 127) {
            throw new IllegalArgumentException("power must be 0..127");
        }
        return editTypedMeta(FireworkMeta.class, meta -> ItemMetaSupport.fireworkPower(meta, power));
    }

    /** Dye leather armour; a no-op on items without leather-armour meta. */
    public ItemBuilder leatherColor(Color color) {
        Objects.requireNonNull(color, "color");
        return editTypedMeta(LeatherArmorMeta.class, meta -> ItemMetaSupport.leatherColor(meta, color));
    }

    /** Set the armour trim; a no-op on items without armour meta. */
    public ItemBuilder armorTrim(ArmorTrim trim) {
        Objects.requireNonNull(trim, "trim");
        return editTypedMeta(ArmorMeta.class, meta -> ItemMetaSupport.armorTrim(meta, trim));
    }

    /** Put a mob inside a spawner item; a no-op on items that hold no block state. */
    public ItemBuilder spawnedType(EntityType type) {
        Objects.requireNonNull(type, "type");
        return editTypedMeta(BlockStateMeta.class, meta -> ItemMetaSupport.spawnedType(meta, type));
    }

    /** Set a written book's title; a no-op on items without book meta. */
    public ItemBuilder bookTitle(Component title) {
        Objects.requireNonNull(title, "title");
        return editTypedMeta(BookMeta.class, meta -> ItemMetaSupport.bookTitle(meta, title));
    }

    /** Set a written book's author; a no-op on items without book meta. */
    public ItemBuilder bookAuthor(Component author) {
        Objects.requireNonNull(author, "author");
        return editTypedMeta(BookMeta.class, meta -> ItemMetaSupport.bookAuthor(meta, author));
    }

    /** Append pages to a written book; a no-op on items without book meta. */
    public ItemBuilder bookPages(Component... pages) {
        Objects.requireNonNull(pages, "pages");
        return editTypedMeta(BookMeta.class, meta -> ItemMetaSupport.bookPages(meta, pages));
    }

    /** Add a stored enchantment (for an enchanted book); a no-op on items without that meta. */
    public ItemBuilder storedEnchant(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "enchantment");
        if (level < 1) {
            throw new IllegalArgumentException("level must be >= 1");
        }
        return editTypedMeta(
                EnchantmentStorageMeta.class, meta -> ItemMetaSupport.storedEnchant(meta, enchantment, level));
    }

    /** Append a banner pattern from a {@code color} and {@code type}; a no-op on items without banner meta. */
    public ItemBuilder bannerPattern(DyeColor color, PatternType type) {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(type, "type");
        return editTypedMeta(BannerMeta.class, meta -> ItemMetaSupport.bannerPattern(meta, color, type));
    }

    /** Append a pre-built banner {@code pattern}; a no-op on items without banner meta. */
    public ItemBuilder bannerPattern(Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        return editTypedMeta(BannerMeta.class, meta -> ItemMetaSupport.bannerPattern(meta, pattern));
    }

    /** Replace every banner pattern with {@code patterns}; a no-op on items without banner meta. */
    public ItemBuilder bannerPatterns(List<Pattern> patterns) {
        Objects.requireNonNull(patterns, "patterns");
        return editTypedMeta(BannerMeta.class, meta -> ItemMetaSupport.bannerPatterns(meta, patterns));
    }

    /** Set a filled map's tint colour; a no-op on items without map meta. */
    public ItemBuilder mapColor(Color color) {
        Objects.requireNonNull(color, "color");
        return editTypedMeta(MapMeta.class, meta -> ItemMetaSupport.mapColor(meta, color));
    }

    /** Set whether a filled map renders at a scaled-out zoom; a no-op on items without map meta. */
    public ItemBuilder mapScaling(boolean scaling) {
        return editTypedMeta(MapMeta.class, meta -> ItemMetaSupport.mapScaling(meta, scaling));
    }

    /** Bind a filled map to a {@link MapView}; a no-op on items without map meta. */
    public ItemBuilder mapView(MapView view) {
        Objects.requireNonNull(view, "view");
        return editTypedMeta(MapMeta.class, meta -> ItemMetaSupport.mapView(meta, view));
    }

    /** Set the on-hover label of a filled map; a no-op on items without map meta. */
    public ItemBuilder mapLocationName(String name) {
        Objects.requireNonNull(name, "name");
        return editTypedMeta(MapMeta.class, meta -> ItemMetaSupport.mapLocationName(meta, name));
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

    /**
     * Build a fresh, independent {@link ItemStack} with everything applied.
     *
     * <p>The copy is Bukkit's copy constructor, which delegates to {@code clone()} on both supported API
     * lines, so a data component written straight onto the stack: {@link #vanillaTooltip} and
     * {@link #hiddenComponents} are the ones that do, rides along with the rest of the item. That is an
     * assumption about somebody else's code, and it is worth writing down because nothing here can catch it
     * breaking: MockBukkit's clone does not model data components, so a test that built an item and read
     * one back would fail today for the wrong reason and pass tomorrow for no reason. If a Minecraft
     * release ever makes this copy shallower, the components go quiet and every test still passes.
     */
    public ItemStack build() {
        return new ItemStack(stack);
    }
}

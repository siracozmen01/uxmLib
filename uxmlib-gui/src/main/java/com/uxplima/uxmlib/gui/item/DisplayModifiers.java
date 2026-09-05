package com.uxplima.uxmlib.gui.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;

/**
 * Ready-made {@link DisplayModifier}s and the glue to attach a pipeline of them to an item. A modifier
 * runs per viewer during render, so each player can see the same slot rendered for themselves: their own
 * head on a skull, their placeholders resolved, their locale's text. Compose with {@link #of} and attach
 * with {@link #apply}.
 */
public final class DisplayModifiers {

    private DisplayModifiers() {}

    /** A pipeline that applies {@code modifiers} left to right. */
    public static DisplayModifier of(DisplayModifier... modifiers) {
        Objects.requireNonNull(modifiers, "modifiers");
        List<DisplayModifier> chain = List.of(modifiers);
        return (context, base) -> {
            ItemStack current = base;
            for (DisplayModifier modifier : chain) {
                current = modifier.modify(context, current);
            }
            return current;
        };
    }

    /** Wrap {@code item} so {@code modifier} is applied to its icon per viewer, keeping its click action. */
    public static GuiItem apply(GuiItem item, DisplayModifier modifier) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(modifier, "modifier");
        return new GuiItem.Dynamic(context -> modifier.modify(context, item.icon(context)), item::action);
    }

    /** Sets the icon (when it is a player head) to show the viewing player's own skin. */
    public static DisplayModifier viewerSkull() {
        return (context, base) -> {
            if (base.getItemMeta() instanceof SkullMeta) {
                return ItemBuilder.from(base)
                        .skull(SkullData.ofUuid(context.viewer().getUniqueId()))
                        .build();
            }
            return base;
        };
    }

    /**
     * Resolves placeholder tokens in the icon's display name through {@code resolver}, against the context's
     * {@link RenderContext#effectivePlayer() effective player} (the placeholder target, which may differ from
     * the viewer): pass {@code com.uxplima.uxmlib.hook.PlaceholderApi::apply} (from uxmlib-integration) for
     * PlaceholderAPI, or any other {@code (player, text) -> text}. Kept as an injected seam so the gui module
     * need not depend on the integration module.
     */
    public static DisplayModifier placeholders(BiFunction<Player, String, String> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        return (context, base) -> {
            Component name =
                    base.getItemMeta() == null ? null : base.getItemMeta().displayName();
            if (name == null) {
                return base;
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(name);
            String resolved = resolver.apply(context.effectivePlayer(), plain);
            return ItemBuilder.from(base).name(Component.text(resolved)).build();
        };
    }

    /**
     * Expands a multi-line lore line into several. Any single source lore line containing {@code token} is
     * split there into multiple lore lines on render, so a config can write one line with embedded breaks
     * (e.g. {@code "Line one|Line two"} with {@code token = "|"}) and have it render as two. Styling is
     * preserved across the split by round-tripping each line through MiniMessage. Lines without the token are
     * passed through unchanged.
     */
    public static DisplayModifier loreSplit(String token) {
        Objects.requireNonNull(token, "token");
        if (token.isEmpty()) {
            throw new IllegalArgumentException("token must not be empty");
        }
        return (context, base) -> {
            ItemMeta meta = base.getItemMeta();
            List<Component> lore = meta == null ? null : meta.lore();
            if (lore == null || lore.isEmpty()) {
                return base;
            }
            return ItemBuilder.from(base).lore(splitLore(lore, token)).build();
        };
    }

    private static List<Component> splitLore(List<Component> lore, String token) {
        MiniMessage mini = MiniMessage.miniMessage();
        List<Component> expanded = new ArrayList<>();
        for (Component line : lore) {
            String serialized = mini.serialize(line);
            if (!serialized.contains(token)) {
                expanded.add(line);
                continue;
            }
            // -1 keeps trailing empty pieces so "a|" expands to two lines, matching what an operator typed.
            for (String piece : serialized.split(java.util.regex.Pattern.quote(token), -1)) {
                expanded.add(mini.deserialize(piece));
            }
        }
        return expanded;
    }
}

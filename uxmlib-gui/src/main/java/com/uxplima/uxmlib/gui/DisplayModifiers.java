package com.uxplima.uxmlib.gui;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.hook.Placeholders;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;

/**
 * Ready-made {@link DisplayModifier}s and the glue to attach a pipeline of them to an item. A modifier
 * runs per viewer during render, so each player can see the same slot rendered for themselves — their own
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

    /** Resolves PlaceholderAPI tokens in the icon's display name for the viewer (no-op without PAPI). */
    public static DisplayModifier placeholders() {
        return (context, base) -> {
            if (!Placeholders.isAvailable()) {
                return base;
            }
            Component name =
                    base.getItemMeta() == null ? null : base.getItemMeta().displayName();
            if (name == null) {
                return base;
            }
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(name);
            String resolved = Placeholders.apply(context.viewer(), plain);
            return ItemBuilder.from(base).name(Component.text(resolved)).build();
        };
    }

    /** Applies an arbitrary per-viewer transform — e.g. resolve a localized name from the viewer's locale. */
    public static DisplayModifier mapping(Function<RenderContext, ItemStack> transform) {
        Objects.requireNonNull(transform, "transform");
        return (context, base) -> transform.apply(context);
    }
}

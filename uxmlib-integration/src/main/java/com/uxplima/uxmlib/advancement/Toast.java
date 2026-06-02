package com.uxplima.uxmlib.advancement;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import org.jspecify.annotations.Nullable;

/**
 * An immutable toast spec — an icon, a title, an optional description and a {@link AdvancementFrame frame}.
 * Build one with {@link #builder()}; the icon and title are required, the description defaults to empty and
 * the frame to {@link AdvancementFrame#TASK}. A spec carries no server state, so it can be built and reused
 * freely, and its {@link #toJson()} is a pure function of its fields.
 *
 * <p>To pop the toast, hand the spec (or a bound builder) to {@link Toasts}: {@code toasts.show(toast,
 * player)}, or build through {@code toasts.builder()...show(player)} for the fluent one-liner. The static
 * {@link #builder()} produces an unbound builder whose terminal is {@link Builder#build()} — useful for
 * pre-building specs and for tests.
 */
public final class Toast {

    private final Material icon;
    private final Component title;
    private final Component description;
    private final AdvancementFrame frame;

    private Toast(Material icon, Component title, Component description, AdvancementFrame frame) {
        this.icon = icon;
        this.title = title;
        this.description = description;
        this.frame = frame;
    }

    /** Start configuring a toast. The result is unbound; call {@link Builder#build()} to get the spec. */
    public static Builder builder() {
        return new Builder(null);
    }

    /** The icon material shown on the toast. */
    public Material icon() {
        return icon;
    }

    /** The toast's bold heading line. */
    public Component title() {
        return title;
    }

    /** The toast's smaller second line; {@link Component#empty()} when none was set. */
    public Component description() {
        return description;
    }

    /** The toast's frame shape. */
    public AdvancementFrame frame() {
        return frame;
    }

    /**
     * The synthetic advancement JSON that drives this toast. Pure: the icon resolves to its item id and the
     * title and description serialise through Adventure's Gson component serialiser (the exact form the
     * vanilla advancement loader reads), so the same spec always yields the same JSON.
     */
    public String toJson() {
        GsonComponentSerializer gson = GsonComponentSerializer.gson();
        return ToastAdvancementJson.build(
                icon.getKey().asString(), gson.serialize(title), gson.serialize(description), frame);
    }

    /** Fluent builder for a {@link Toast}. Bound builders (from {@link Toasts}) also expose {@code show}. */
    public static final class Builder {

        private final @Nullable Toasts service;
        private @Nullable Material icon;
        private @Nullable Component title;
        private Component description = Component.empty();
        private AdvancementFrame frame = AdvancementFrame.TASK;

        Builder(@Nullable Toasts service) {
            this.service = service;
        }

        /** Set the toast's icon from an item; only its {@link Material} is used (count and meta are ignored). */
        public Builder icon(ItemStack item) {
            Objects.requireNonNull(item, "item");
            this.icon = item.getType();
            return this;
        }

        /** Set the toast's icon material directly. */
        public Builder icon(Material material) {
            this.icon = Objects.requireNonNull(material, "material");
            return this;
        }

        /** Set the toast's heading. Required. */
        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** Set the toast's optional second line. */
        public Builder description(Component description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        /** Set the toast's frame shape. Defaults to {@link AdvancementFrame#TASK}. */
        public Builder frame(AdvancementFrame frame) {
            this.frame = Objects.requireNonNull(frame, "frame");
            return this;
        }

        /** Finish the immutable spec. The icon and title must have been set. */
        public Toast build() {
            Objects.requireNonNull(icon, "icon must be set");
            Objects.requireNonNull(title, "title must be set");
            return new Toast(icon, title, description, frame);
        }

        /**
         * Build the spec and pop it for {@code player}. Only available on a bound builder obtained from
         * {@link Toasts#builder()}; an unbound builder (from {@link Toast#builder()}) cannot show a toast
         * because it has no server services, and throws to say so.
         */
        public void show(Player player) {
            Objects.requireNonNull(player, "player");
            if (service == null) {
                throw new IllegalStateException(
                        "Toast.builder() is unbound; obtain a builder from Toasts#builder() to show a toast");
            }
            service.show(build(), player);
        }
    }
}

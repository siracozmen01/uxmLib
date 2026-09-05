package com.uxplima.uxmlib.menu;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.menu.property.EditableProperty;
import org.jspecify.annotations.Nullable;

/**
 * The editor analog of a {@link MenuSpec}: the recipe the engine draws a typed property editor from. It carries
 * the {@link EntityEditorLayout} (rows, the ordered property slots, the back and delete slots and their materials),
 * a per-viewer title, the catalog line each property's current value renders into, the back label, an optional
 * delete label with its confirm title, the function that derives the live property list from the subject, and the
 * back and delete callbacks.
 *
 * <p>This is the engine's editor surface, opened through {@link Menus#openEditor}, and it is type-erased on the
 * subject ({@code Object}): the engine routes clicks and re-renders without ever naming the edited type. A caller
 * that wants its own type back closes over it in the property provider and the callbacks, which is what
 * {@link com.uxplima.uxmlib.menu.EntityEditorView} does.
 *
 * <p>Every label, value line and title is either a catalog key or a {@link Component} the caller already resolved,
 * so an editor opened through this spec carries no user-facing literal of the engine's own.
 */
public final class EditorSpec {

    private final EntityEditorLayout layout;
    private final BiFunction<Player, @Nullable Object, Component> title;
    private final String valueLore;
    private final String backName;
    private final @Nullable String deleteName;
    private final @Nullable String deleteConfirmTitle;
    private final Function<@Nullable Object, List<EditableProperty>> properties;
    private final Consumer<Player> onBack;
    private final @Nullable BiConsumer<Player, @Nullable Object> onDelete;

    private EditorSpec(Builder builder) {
        this.layout = Objects.requireNonNull(builder.layout, "layout");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.valueLore = Objects.requireNonNull(builder.valueLore, "valueLore");
        this.backName = Objects.requireNonNull(builder.backName, "backName");
        this.deleteName = builder.deleteName;
        this.deleteConfirmTitle = builder.deleteConfirmTitle;
        this.properties = Objects.requireNonNull(builder.properties, "properties");
        this.onBack = Objects.requireNonNull(builder.onBack, "onBack");
        this.onDelete = builder.onDelete;
    }

    /** Start building an editor spec; required fields are validated at {@link Builder#build}. */
    public static Builder builder() {
        return new Builder();
    }

    public EntityEditorLayout layout() {
        return layout;
    }

    public String valueLore() {
        return valueLore;
    }

    public String backName() {
        return backName;
    }

    public Optional<String> deleteName() {
        return Optional.ofNullable(deleteName);
    }

    public Optional<String> deleteConfirmTitle() {
        return Optional.ofNullable(deleteConfirmTitle);
    }

    public Consumer<Player> onBack() {
        return onBack;
    }

    public Optional<BiConsumer<Player, @Nullable Object>> onDelete() {
        return Optional.ofNullable(onDelete);
    }

    /** Resolve the editor title for {@code viewer} editing {@code subject}, the same per-open title the view shows. */
    public Component title(Player viewer, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        return title.apply(viewer, subject);
    }

    /** The live property list for {@code subject}, re-read on every draw, so an edit shows without a reopen. */
    public List<EditableProperty> propertiesFor(@Nullable Object subject) {
        return properties.apply(subject);
    }

    /** Whether the optional delete button is fully configured (label, confirm title, handler, and a layout slot). */
    public boolean hasDelete() {
        return onDelete != null
                && deleteName != null
                && deleteConfirmTitle != null
                && layout.deleteSlot().isPresent();
    }

    /** Fluent builder; required fields are checked at {@link #build}. */
    public static final class Builder {
        private @Nullable EntityEditorLayout layout;
        private @Nullable BiFunction<Player, @Nullable Object, Component> title;
        private @Nullable String valueLore;
        private @Nullable String backName;
        private @Nullable String deleteName;
        private @Nullable String deleteConfirmTitle;
        private @Nullable Function<@Nullable Object, List<EditableProperty>> properties;
        private @Nullable Consumer<Player> onBack;
        private @Nullable BiConsumer<Player, @Nullable Object> onDelete;

        private Builder() {}

        public Builder layout(EntityEditorLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        /** The editor title, resolved per viewer and subject (a caller wraps the subject name in {@code <value>}). */
        public Builder title(BiFunction<Player, @Nullable Object, Component> title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /** The catalog line each property's current value renders into (carries a {@code {value}} placeholder). */
        public Builder valueLore(String valueLore) {
            this.valueLore = Objects.requireNonNull(valueLore, "valueLore");
            return this;
        }

        public Builder backName(String backName) {
            this.backName = Objects.requireNonNull(backName, "backName");
            return this;
        }

        public Builder properties(Function<@Nullable Object, List<EditableProperty>> properties) {
            this.properties = Objects.requireNonNull(properties, "properties");
            return this;
        }

        public Builder onBack(Consumer<Player> onBack) {
            this.onBack = Objects.requireNonNull(onBack, "onBack");
            return this;
        }

        /** Wire the optional delete button: its name, the confirm title, and the delete handler. */
        public Builder onDelete(
                String deleteName, String deleteConfirmTitle, BiConsumer<Player, @Nullable Object> onDelete) {
            this.deleteName = Objects.requireNonNull(deleteName, "deleteName");
            this.deleteConfirmTitle = Objects.requireNonNull(deleteConfirmTitle, "deleteConfirmTitle");
            this.onDelete = Objects.requireNonNull(onDelete, "onDelete");
            return this;
        }

        /** Build the spec; the constructor validates that every required field was set. */
        public EditorSpec build() {
            return new EditorSpec(this);
        }
    }
}

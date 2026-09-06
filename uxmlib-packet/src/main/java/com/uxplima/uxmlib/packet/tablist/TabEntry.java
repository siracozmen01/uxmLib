package com.uxplima.uxmlib.packet.tablist;

import java.util.Objects;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * One fully-controlled tab-list row, expressed as a pure value (an Adventure {@link Component}, no NMS) so the
 * {@link TabListPackets} port and its callers stay server-free. The NMS builder behind the port maps this onto
 * a {@code ClientboundPlayerInfoUpdatePacket.Entry}: the {@link #id} becomes the entry's profile id, the
 * {@link #name} the {@code GameProfile} name, the {@link #skin} the {@code textures} property, and the
 * {@link #displayName}/{@link #listOrder} the rendered row.
 *
 * <p>This is for entries you own end to end (a fake player, a header/spacer row, a custom-skinned slot): the
 * per-viewer things native Paper cannot do. For a real online player you would not re-add a synthetic entry.
 *
 * @param id the entry's profile id; for a synthetic row this is any stable {@code UUID} you mint
 * @param displayName the component shown in the tab list for this row
 * @param listOrder the sort key the client uses to order the list (higher sorts later, like vanilla)
 * @param skin the custom skin texture, or {@code null} for no skin override
 * @param name the {@code GameProfile} name an add-entry needs; defaults to the id string when {@code null}
 */
public record TabEntry(
        UUID id,
        Component displayName,
        int listOrder,
        @Nullable TabSkin skin,
        @Nullable String name) {

    /** Mojang caps a {@code GameProfile} name at 16 characters; a longer one makes the profile ctor throw. */
    private static final int MAX_PROFILE_NAME_LENGTH = 16;

    public TabEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }

    /** A minimal entry: just an id and a display name, default list order, no skin, name defaulted from the id. */
    public static TabEntry of(UUID id, Component displayName) {
        return new TabEntry(id, displayName, 0, null, null);
    }

    /**
     * A synthetic "filler" row backed by no real player: a fresh random id and an empty profile name, so it
     * never collides with an online player or trips the 16-char profile-name limit. On 1.21.2+ clients the
     * tab list sorts purely by {@link #listOrder}, so an empty name positions the slot fine.
     *
     * @param listOrder the sort key the client uses to order this row
     * @param displayName the component shown for the row
     * @param skin the custom skin texture, or {@code null} for none
     */
    public static TabEntry filler(int listOrder, Component displayName, @Nullable TabSkin skin) {
        return new TabEntry(UUID.randomUUID(), displayName, listOrder, skin, "");
    }

    /**
     * The {@code GameProfile} name to use for this entry: the explicit {@link #name} when set, otherwise the
     * id rendered as a string. A profile always needs a name; the client never displays it (the display name
     * does), so the id string is a safe, stable fallback. The result is capped to {@value #MAX_PROFILE_NAME_LENGTH}
     * characters so a long name (or the id fallback) can never reach the profile ctor and throw.
     */
    public String profileName() {
        String resolved = name != null ? name : id.toString();
        return resolved.length() <= MAX_PROFILE_NAME_LENGTH ? resolved : resolved.substring(0, MAX_PROFILE_NAME_LENGTH);
    }

    /** Convert this legacy convenience value into the complete player-info entry model. */
    public PlayerInfoEntry toPlayerInfoEntry() {
        return PlayerInfoEntry.from(this);
    }
}

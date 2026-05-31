package com.uxplima.uxmlib.hook;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;

/**
 * A present-guarded view of LuckPerms. {@link #find()} returns empty when LuckPerms is absent, so callers
 * degrade gracefully; the {@code net.luckperms} classes are touched only past that guard, so a server
 * without LuckPerms still loads. Lookups use the cached user data, which is populated for online players,
 * so the accessors are non-blocking and return empty for an unknown or uncached player.
 */
public final class LuckPermsHook {

    private final LuckPerms luckPerms;

    private LuckPermsHook(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /** The LuckPerms hook, or empty when LuckPerms is not installed. */
    public static Optional<LuckPermsHook> find() {
        if (!Hooks.isPresent("LuckPerms")) {
            return Optional.empty();
        }
        return Optional.of(new LuckPermsHook(LuckPermsProvider.get()));
    }

    /** A player's chat prefix, if any. */
    public Optional<String> prefix(Player player) {
        return meta(player).flatMap(data -> Optional.ofNullable(data.getPrefix()));
    }

    /** A player's chat suffix, if any. */
    public Optional<String> suffix(Player player) {
        return meta(player).flatMap(data -> Optional.ofNullable(data.getSuffix()));
    }

    /** A player's primary group, if known. */
    public Optional<String> primaryGroup(Player player) {
        return meta(player).flatMap(data -> Optional.ofNullable(data.getPrimaryGroup()));
    }

    /** A custom meta value for a player by key, if set. */
    public Optional<String> metaValue(Player player, String key) {
        Objects.requireNonNull(key, "key");
        return meta(player).flatMap(data -> Optional.ofNullable(data.getMetaValue(key)));
    }

    private Optional<CachedMetaData> meta(Player player) {
        Objects.requireNonNull(player, "player");
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null
                ? Optional.empty()
                : Optional.of(user.getCachedData().getMetaData());
    }
}

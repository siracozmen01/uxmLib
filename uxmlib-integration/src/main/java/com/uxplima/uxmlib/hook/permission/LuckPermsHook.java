package com.uxplima.uxmlib.hook.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.hook.Hooks;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.jspecify.annotations.Nullable;

/**
 * A present-guarded view of LuckPerms. {@link #find()} returns empty when LuckPerms is absent, so callers
 * degrade gracefully; the {@code net.luckperms} classes are touched only past that guard, so a server
 * without LuckPerms still loads. The {@link Player} accessors use the cached user data, which is populated
 * for online players, so they are non-blocking and return empty for an unknown or uncached player. The
 * {@code *Async(UUID)} accessors load an <em>offline</em> user through LuckPerms' own async user manager —
 * widening prefix/suffix/group coverage to players who are not online (leaderboards, PAPI).
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

    /**
     * The offline user's chat prefix. Loads the user through LuckPerms' async user manager (off the main
     * thread, in LuckPerms' own pool), so the result is delivered on the returned future; the future yields
     * empty if the user has no prefix or has no stored data.
     */
    public CompletableFuture<Optional<String>> prefixAsync(UUID uuid) {
        return metaAsync(uuid, CachedMetaData::getPrefix);
    }

    /** The offline user's chat suffix, loaded through LuckPerms' async user manager. */
    public CompletableFuture<Optional<String>> suffixAsync(UUID uuid) {
        return metaAsync(uuid, CachedMetaData::getSuffix);
    }

    /** The offline user's primary group, loaded through LuckPerms' async user manager. */
    public CompletableFuture<Optional<String>> primaryGroupAsync(UUID uuid) {
        return metaAsync(uuid, CachedMetaData::getPrimaryGroup);
    }

    /** A custom meta value for an offline user by key, loaded through LuckPerms' async user manager. */
    public CompletableFuture<Optional<String>> metaValueAsync(UUID uuid, String key) {
        Objects.requireNonNull(key, "key");
        return metaAsync(uuid, data -> data.getMetaValue(key));
    }

    private CompletableFuture<Optional<String>> metaAsync(UUID uuid, Function<CachedMetaData, @Nullable String> field) {
        Objects.requireNonNull(uuid, "uuid");
        // loadUser is already async on LuckPerms' own pool — return its future directly rather than wrapping
        // it in our Scheduler, which would double-hop and risk touching the Bukkit API off-thread.
        return luckPerms.getUserManager().loadUser(uuid).thenApply(user -> extract(user, field));
    }

    /** Pure extraction of one meta field from a loaded user; null-safe so a missing field yields empty. */
    static Optional<String> extract(@Nullable User user, Function<CachedMetaData, @Nullable String> field) {
        if (user == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(field.apply(user.getCachedData().getMetaData()));
    }

    private Optional<CachedMetaData> meta(Player player) {
        Objects.requireNonNull(player, "player");
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null
                ? Optional.empty()
                : Optional.of(user.getCachedData().getMetaData());
    }
}

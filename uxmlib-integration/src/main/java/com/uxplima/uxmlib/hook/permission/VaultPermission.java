package com.uxplima.uxmlib.hook.permission;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.uxplima.uxmlib.hook.Hooks;
import net.milkbowl.vault.permission.Permission;

/**
 * A present-guarded view of the Vault permission service, so a plugin can read and edit permissions
 * provider-agnostically (LuckPerms, PEX, …) without depending on any one. As with {@link com.uxplima.uxmlib.hook.economy.VaultEconomy},
 * the {@code net.milkbowl} classes are touched only inside {@link #find()} past the registration check, so
 * a server without Vault still loads. These calls may block — route writes off the main thread.
 */
public final class VaultPermission {

    private final Permission permission;

    private VaultPermission(Permission permission) {
        this.permission = permission;
    }

    /** The registered Vault permission provider, or empty when Vault or a provider is absent. */
    public static Optional<VaultPermission> find() {
        if (!Hooks.isPresent("Vault")) {
            return Optional.empty();
        }
        RegisteredServiceProvider<Permission> registration =
                Bukkit.getServicesManager().getRegistration(Permission.class);
        if (registration == null) {
            return Optional.empty();
        }
        Permission provider = Objects.requireNonNull(registration.getProvider(), "provider");
        return Optional.of(new VaultPermission(provider));
    }

    /** Whether {@code player} has {@code node}. */
    public boolean has(OfflinePlayer player, String node) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        return permission.playerHas(null, player, node);
    }

    /** Grant {@code node} to {@code player}; returns whether it changed. */
    public boolean add(OfflinePlayer player, String node) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        return permission.playerAdd(null, player, node);
    }

    /** Revoke {@code node} from {@code player}; returns whether it changed. */
    public boolean remove(OfflinePlayer player, String node) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(node, "node");
        return permission.playerRemove(null, player, node);
    }

    /** The player's primary group, or empty when the provider has none. */
    public Optional<String> primaryGroup(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        return Optional.ofNullable(permission.getPrimaryGroup(null, player));
    }
}

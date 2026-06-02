package com.uxplima.uxmlib.hook.economy;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.uxplima.uxmlib.hook.Hooks;
import net.milkbowl.vault.economy.Economy;

/**
 * A present-guarded view of the Vault economy. Vault itself registers no economy; an economy plugin
 * (EssentialsX and the like) registers a provider with the {@code ServicesManager}, so {@link #find()}
 * looks one up and returns empty when none is present. The {@code net.milkbowl} classes are touched only
 * inside {@link #find()} past the registration check, so a server without Vault still loads.
 */
public final class VaultEconomy {

    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    /** The registered Vault economy, or empty when Vault or an economy provider is absent. */
    public static Optional<VaultEconomy> find() {
        if (!Hooks.isPresent("Vault")) {
            return Optional.empty();
        }
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return Optional.empty();
        }
        // A registration always carries a non-null provider; the unannotated Vault API hides that from NullAway.
        Economy provider = Objects.requireNonNull(registration.getProvider(), "provider");
        return Optional.of(new VaultEconomy(provider));
    }

    /** A player's balance. */
    public double balance(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        return economy.getBalance(player);
    }

    /** Whether a player has at least {@code amount}. */
    public boolean has(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return economy.has(player, amount);
    }

    /** Withdraw {@code amount} from a player; returns whether the transaction succeeded. */
    public boolean withdraw(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** Deposit {@code amount} to a player; returns whether the transaction succeeded. */
    public boolean deposit(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /** The provider's own rendering of {@code amount}. */
    public String format(double amount) {
        // The unannotated Vault API can hand back null; a bridge must never propagate one.
        return Objects.requireNonNullElse(economy.format(amount), "");
    }

    /** The currency's singular name. */
    public String currencyNameSingular() {
        return Objects.requireNonNullElse(economy.currencyNameSingular(), "");
    }

    /** The currency's plural name. */
    public String currencyNamePlural() {
        return Objects.requireNonNullElse(economy.currencyNamePlural(), "");
    }
}

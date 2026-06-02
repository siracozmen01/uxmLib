package com.uxplima.uxmlib.hook.economy;

import java.util.Objects;

import org.bukkit.OfflinePlayer;

/** An {@link EconomyBridge} backed by the present-guarded {@link VaultEconomy} view. */
final class VaultEconomyBridge implements EconomyBridge {

    private final VaultEconomy vault;

    VaultEconomyBridge(VaultEconomy vault) {
        this.vault = Objects.requireNonNull(vault, "vault");
    }

    @Override
    public double balance(OfflinePlayer player) {
        return vault.balance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return vault.has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return vault.withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return vault.deposit(player, amount);
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public String format(double amount) {
        return vault.format(amount);
    }

    @Override
    public String currencySymbol() {
        // Vault exposes no separate symbol; its singular currency name is the closest stable surface.
        return vault.currencyNameSingular();
    }

    @Override
    public String currencyNameSingular() {
        return vault.currencyNameSingular();
    }

    @Override
    public String currencyNamePlural() {
        return vault.currencyNamePlural();
    }
}

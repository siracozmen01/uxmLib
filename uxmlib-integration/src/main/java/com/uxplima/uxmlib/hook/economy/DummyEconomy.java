package com.uxplima.uxmlib.hook.economy;

import org.bukkit.OfflinePlayer;

/**
 * The no-op economy returned by {@link EconomyBridge#orDummy()} when no economy plugin is installed:
 * everyone has a zero balance and every transaction fails. Lets call sites treat "no economy" the same as
 * "transaction declined" without a null check.
 */
final class DummyEconomy implements EconomyBridge {

    @Override
    public double balance(OfflinePlayer player) {
        return 0.0d;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        return false;
    }

    @Override
    public boolean isPresent() {
        return false;
    }
}

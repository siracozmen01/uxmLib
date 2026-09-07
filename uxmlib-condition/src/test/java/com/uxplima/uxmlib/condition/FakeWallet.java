package com.uxplima.uxmlib.condition;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;

/**
 * A wallet held in a map, so the money condition and the {@code [take-money]} action can be driven without a
 * server or an economy plugin. It records every withdrawal so a test can prove that a refused take spent
 * nothing.
 */
public final class FakeWallet implements Wallet {

    private final Map<String, Double> balances = new HashMap<>();
    private int withdrawals;

    public FakeWallet(String currency, double balance) {
        balances.put(currency, balance);
    }

    @Override
    public double balance(@Nullable Player player, String currency) {
        return balances.getOrDefault(currency, 0.0);
    }

    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        double held = balance(player, currency);
        if (amount <= 0 || held < amount) {
            return false;
        }
        withdrawals++;
        balances.put(currency, held - amount);
        return true;
    }

    /** How many withdrawals were actually applied; a refused take must leave this untouched. */
    public int withdrawals() {
        return withdrawals;
    }
}

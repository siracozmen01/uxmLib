package com.uxplima.uxmlib.hook;

import java.util.Optional;

import org.bukkit.OfflinePlayer;

/**
 * A provider-agnostic economy view, so call sites depend on this and not on Vault directly. {@link #find}
 * picks the best available backend (today: Vault); {@link #orDummy} returns a no-op {@link DummyEconomy}
 * when none is present, so a caller never has to null-check. Reads/writes may block on the backing
 * economy plugin — route them off the main thread via the scheduler.
 */
public interface EconomyBridge {

    /** The player's balance, or {@code 0} on a dummy economy. */
    double balance(OfflinePlayer player);

    /** Whether the player has at least {@code amount}. */
    boolean has(OfflinePlayer player, double amount);

    /** Take {@code amount} from the player; returns whether it succeeded. */
    boolean withdraw(OfflinePlayer player, double amount);

    /** Give {@code amount} to the player; returns whether it succeeded. */
    boolean deposit(OfflinePlayer player, double amount);

    /** Whether this is a real economy (false for the dummy null-object). */
    boolean isPresent();

    /** The best available economy backend, or empty when none is installed. */
    static Optional<EconomyBridge> find() {
        return VaultEconomy.find().map(VaultEconomyBridge::new);
    }

    /** The best available backend, or a no-op {@link DummyEconomy} so call sites never null-check. */
    static EconomyBridge orDummy() {
        return find().orElseGet(DummyEconomy::new);
    }
}

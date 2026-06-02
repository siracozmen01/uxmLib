package com.uxplima.uxmlib.hook.economy;

import java.util.Objects;

import org.bukkit.OfflinePlayer;

/**
 * An {@link EconomyBridge} that holds whichever real backend is currently bound and falls back to a no-op
 * {@link DummyEconomy} when none is. An economy provider can register its Vault service <em>after</em> our
 * plugin has loaded (or unregister at runtime), so this bridge can be {@linkplain #rebind() re-resolved}
 * the moment that happens — see {@link EconomyServiceListener}. Every call forwards to the live delegate,
 * so call sites keep one stable reference across a provider swap. The delegate is read/written under one
 * lock so a swap is atomic with respect to a forwarded call.
 */
public final class RebindingEconomyBridge implements EconomyBridge {

    private final Object lock = new Object();
    private EconomyBridge delegate;

    /** Start unbound (the dummy economy) and resolve the best backend immediately. */
    public RebindingEconomyBridge() {
        this.delegate = EconomyBridge.orDummy();
    }

    /** Re-resolve the best available backend; drops to the dummy economy when none is present. */
    public void rebind() {
        EconomyBridge resolved = EconomyBridge.orDummy();
        synchronized (lock) {
            this.delegate = resolved;
        }
    }

    /** Drop any real backend back to the dummy economy (a provider unregistered). */
    public void unbind() {
        EconomyBridge dummy = new DummyEconomy();
        synchronized (lock) {
            this.delegate = dummy;
        }
    }

    /** The backend currently forwarded to (the dummy economy when unbound). */
    public EconomyBridge current() {
        synchronized (lock) {
            return delegate;
        }
    }

    @Override
    public double balance(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        return current().balance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return current().has(player, amount);
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return current().withdraw(player, amount);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return current().deposit(player, amount);
    }

    @Override
    public boolean isPresent() {
        return current().isPresent();
    }

    @Override
    public String format(double amount) {
        return current().format(amount);
    }

    @Override
    public String currencySymbol() {
        return current().currencySymbol();
    }

    @Override
    public String currencyNameSingular() {
        return current().currencyNameSingular();
    }

    @Override
    public String currencyNamePlural() {
        return current().currencyNamePlural();
    }
}

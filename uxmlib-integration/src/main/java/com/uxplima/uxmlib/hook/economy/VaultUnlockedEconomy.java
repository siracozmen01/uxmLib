package com.uxplima.uxmlib.hook.economy;

import java.util.Optional;

import com.uxplima.uxmlib.hook.Hooks;

/**
 * A present-guarded view of the VaultUnlocked economy — the {@code net.milkbowl.vault2.economy} provider,
 * a separate plugin from classic Vault with a {@code BigDecimal}, UUID-keyed, multi-currency API. {@link
 * #find()} returns empty when VaultUnlocked is not installed, so {@link EconomyBridge#find()} can try it
 * alongside classic Vault and fall through cleanly when it is absent.
 *
 * <p>The {@code vault2} API is not yet on this module's compile classpath, so the operational surface
 * (balance/withdraw/deposit/format against {@code net.milkbowl.vault2.economy.Economy}) cannot be bound here
 * without either adding that dependency or resorting to reflection — the latter is disallowed. Until the
 * dependency is added, this view only reports presence; once {@code vault2} is a {@code compileOnly}
 * dependency, the service lookup and a {@code VaultUnlockedEconomyBridge} slot in behind the same guard.
 */
public final class VaultUnlockedEconomy {

    private VaultUnlockedEconomy() {}

    /**
     * The registered VaultUnlocked economy, or empty when the plugin is absent. Operational binding awaits
     * the {@code vault2} API on the compile classpath (see the class note), so this currently yields empty
     * whenever the API is unavailable rather than constructing a half-wired bridge.
     */
    public static Optional<VaultUnlockedEconomy> find() {
        if (!Hooks.isPresent("VaultUnlocked")) {
            return Optional.empty();
        }
        // The vault2 service can only be looked up with its API Class on the classpath, which this module
        // does not yet depend on. Report absent so EconomyBridge.find() falls through to classic Vault.
        return Optional.empty();
    }

    /**
     * Adapt this view to an {@link EconomyBridge}. Empty until the {@code vault2} API is on the compile
     * classpath: with no operational binding to construct, a present view still cannot back a bridge, so the
     * wiring in {@link EconomyBridge#find()} falls through to the dummy economy. This is the single seam to
     * fill once the dependency lands — a {@code VaultUnlockedEconomyBridge} returns here.
     */
    Optional<EconomyBridge> toBridge() {
        return Optional.empty();
    }
}

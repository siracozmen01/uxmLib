/**
 * The economy integration: {@link com.uxplima.uxmlib.hook.economy.EconomyBridge} is a provider-agnostic
 * economy view whose {@code find} picks the best available backend (classic Vault, via
 * {@link com.uxplima.uxmlib.hook.economy.VaultEconomy}, then VaultUnlocked, via
 * {@link com.uxplima.uxmlib.hook.economy.VaultUnlockedEconomy}) and whose {@code orDummy} returns a no-op
 * null-object, so call sites never null-check. The bridge also surfaces the provider's
 * {@code format}/currency names. {@link com.uxplima.uxmlib.hook.economy.CachedEconomyBridge} decorates a
 * bridge with a short TTL balance cache, and {@link com.uxplima.uxmlib.hook.economy.RebindingEconomyBridge}
 * plus {@link com.uxplima.uxmlib.hook.economy.EconomyServiceListener} keep one stable reference current as
 * an economy provider registers or unregisters its Vault service at runtime. Each third-party symbol is
 * reached only past a presence guard, so a server without the economy plugin still loads.
 */
@NullMarked
package com.uxplima.uxmlib.hook.economy;

import org.jspecify.annotations.NullMarked;

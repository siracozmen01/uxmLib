/**
 * The economy integration: {@link com.uxplima.uxmlib.hook.economy.EconomyBridge} is a provider-agnostic
 * economy view whose {@code find} picks the best available backend (Vault, via
 * {@link com.uxplima.uxmlib.hook.economy.VaultEconomy}) and whose {@code orDummy} returns a no-op
 * null-object, so call sites never null-check. Each third-party symbol is reached only past a presence
 * guard, so a server without the economy plugin still loads.
 */
@NullMarked
package com.uxplima.uxmlib.hook.economy;

import org.jspecify.annotations.NullMarked;

/**
 * Soft-dependency hooks. {@link com.uxplima.uxmlib.hook.Hooks} answers whether a plugin is present;
 * {@link com.uxplima.uxmlib.hook.Placeholders} (PlaceholderAPI) and
 * {@link com.uxplima.uxmlib.hook.VaultEconomy} (Vault) are present-guarded bridges that no-op or return
 * empty when their plugin is absent, and {@link com.uxplima.uxmlib.hook.PluginHook} is the shared
 * contract. Each third-party symbol is reached only past its presence guard, so a server without the
 * plugin still loads.
 */
@NullMarked
package com.uxplima.uxmlib.hook;

import org.jspecify.annotations.NullMarked;

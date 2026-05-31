package com.uxplima.uxmlib.plugin;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lifecycle shell for the standalone distribution. It holds no state and registers nothing — the
 * library's value is its API surface, which dependent plugins call directly.
 */
public final class UxmLibPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger()
                .info("uxmLib " + getPluginMeta().getVersion() + " loaded — toolkit available to dependent plugins.");
    }
}

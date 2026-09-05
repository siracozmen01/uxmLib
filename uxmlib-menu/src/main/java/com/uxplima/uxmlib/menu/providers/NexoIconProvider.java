package com.uxplima.uxmlib.menu.providers;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.common.Log;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a {@code nexo:<id>} spec to its Nexo custom item. Nexo (the maintained successor to Oraxen) exposes a static
 * facade {@code com.nexomc.nexo.api.NexoItems.itemFromId(String)} that returns a Nexo {@code ItemBuilder} (or {@code
 * null} for an unknown id), whose {@code build()} yields the finished {@link ItemStack}.
 *
 * <p>No {@code com.nexomc} type is named here (the SDK is reached only by string class-name through reflection) so a
 * server without Nexo loads none of its classes and the present-guard in {@link ReflectiveItemProvider} short-circuits
 * before any lookup runs.
 */
final class NexoIconProvider extends ReflectiveItemProvider {

    private static final String PLUGIN_NAME = "Nexo";
    private static final String PREFIX = "nexo:";
    private static final String ITEMS_CLASS = "com.nexomc.nexo.api.NexoItems";

    NexoIconProvider(Server server, Log log) {
        super(PLUGIN_NAME, PREFIX, server, log);
    }

    @Override
    protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
        Object builder =
                Class.forName(ITEMS_CLASS).getMethod("itemFromId", String.class).invoke(null, id);
        if (builder == null) {
            return null;
        }
        return (ItemStack) builder.getClass().getMethod("build").invoke(builder);
    }
}

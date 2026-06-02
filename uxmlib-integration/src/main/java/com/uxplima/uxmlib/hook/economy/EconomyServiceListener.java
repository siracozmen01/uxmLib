package com.uxplima.uxmlib.hook.economy;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;

/**
 * Keeps a {@link RebindingEconomyBridge} current with the server's economy registration. An economy plugin
 * registers its Vault {@code Economy} service with the {@code ServicesManager}, sometimes <em>after</em>
 * our plugin has already loaded; binding on plugin-enable alone can miss that. Registering this listener
 * (from the plugin shell — the only place with a {@code Plugin} to register against) re-resolves the
 * bridge the moment an economy service registers, and drops it when one unregisters.
 *
 * <p>The handlers compare the registered service by class <em>name</em> rather than referencing the Vault
 * {@code Economy} class directly, so this listener loads on a server without Vault and simply never matches.
 */
public final class EconomyServiceListener implements Listener {

    /** The Vault economy service class, matched by name so we never hard-load it. */
    static final String ECONOMY_SERVICE = "net.milkbowl.vault.economy.Economy";

    private final RebindingEconomyBridge bridge;

    public EconomyServiceListener(RebindingEconomyBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        Objects.requireNonNull(event, "event");
        if (isEconomyService(event.getProvider().getService())) {
            bridge.rebind();
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        Objects.requireNonNull(event, "event");
        if (isEconomyService(event.getProvider().getService())) {
            // Re-resolve rather than blindly unbind: another provider may still be registered.
            bridge.rebind();
        }
    }

    private static boolean isEconomyService(Class<?> service) {
        return ECONOMY_SERVICE.equals(service.getName());
    }
}

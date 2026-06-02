package com.uxplima.uxmlib.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The single Bukkit listener that drives every {@link Gui}. Because a menu is its inventory's holder,
 * each event is routed back to the owning menu via {@code getInventory().getHolder()}; events for
 * inventories the framework does not own are ignored. Registered once with {@link Guis#install}.
 *
 * <p>The listener owns a per-viewer {@link ClickGuard}: a viewer's clicks that arrive inside the debounce
 * window are cancelled and their slot action is dropped. When a {@link Scheduler} was installed, an
 * accepted click's slot action is deferred to the next tick on the viewer's region thread, so opening
 * another inventory inside a handler does not desync (the classic open-inside-click dupe).
 */
public final class GuiListener implements Listener {

    private final ClickGuard clickGuard = new ClickGuard();

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AbstractGui gui)) {
            return;
        }
        // The cancel policy must run in-event; only the slot action is debounced or deferred.
        gui.applyClickPolicy(event);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!clickGuard.accept(player)) {
            return; // spam-click within the debounce window: keep the cancel, drop the action
        }
        Scheduler scheduler = installedScheduler();
        if (scheduler == null) {
            gui.dispatchClick(event);
        } else {
            scheduler.entity(player, () -> gui.dispatchClick(event));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.handleDrag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.handleClose(event);
            clickGuard.forget(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof Gui gui) {
            gui.handleOpen(event);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // A viewer can leave via a path that never delivers an InventoryCloseEvent for the menu (kick, world
        // unload). Prune their debounce entry on quit so the table stays bounded to online players.
        clickGuard.forget(event.getPlayer().getUniqueId());
    }

    private static @org.jspecify.annotations.Nullable Scheduler installedScheduler() {
        GuiRegistry registry = Guis.registry();
        return registry == null ? null : registry.scheduler();
    }
}

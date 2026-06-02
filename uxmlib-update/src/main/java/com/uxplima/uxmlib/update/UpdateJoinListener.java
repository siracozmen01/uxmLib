package com.uxplima.uxmlib.update;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On join, tells a permission-holding player that a newer build exists, with a click-to-open link. The check
 * is permission-gated on a node (not {@code isOp()}) so a server owner controls exactly who sees it. If the
 * cache is still cold when a player joins (the first poll hasn't returned yet), a fresh check is queued so the
 * next join is warm — the join itself never blocks on the network, matching the established re-queue pattern.
 *
 * <p>Owned and registered by {@link UpdateNotifier}; package-private because the wiring is the public surface.
 */
final class UpdateJoinListener implements Listener {

    private final UpdateChecker checker;
    private final String pluginName;
    private final String permission;

    UpdateJoinListener(UpdateChecker checker, String pluginName, String permission) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.permission = Objects.requireNonNull(permission, "permission");
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(permission)) {
            return;
        }
        checker.lastOutcome().ifPresentOrElse(outcome -> notifyIfOutdated(player, outcome), this::warmCache);
    }

    private void notifyIfOutdated(Player player, UpdateOutcome outcome) {
        if (!outcome.isOutdated()) {
            return;
        }
        outcome.release()
                .ifPresent(release -> player.sendMessage(UpdateMessages.notification(
                        pluginName, checker.currentVersion().toString(), release)));
    }

    private void warmCache() {
        // The first poll hasn't completed yet; kick off a check so a later join finds a warm cache. The future
        // is intentionally not awaited — the join must not block, and the result is read on the next join.
        var ignored = checker.check();
    }
}

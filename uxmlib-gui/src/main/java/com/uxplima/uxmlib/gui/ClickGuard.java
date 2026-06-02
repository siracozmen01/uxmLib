package com.uxplima.uxmlib.gui;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

/**
 * Per-viewer click debounce. A viewer's clicks that arrive within a short window of their last accepted
 * click are dropped, which kills the shift-spam dupe and the rapid double-fire that desyncs a menu when a
 * handler opens another inventory. State is held on the instance (one per {@link GuiListener}), never
 * statically, so two plugins each with their own install do not share a cooldown table.
 *
 * <p>The window defaults to ~150ms, matching the debounce CMILib and AdvancedCore settled on. The map is
 * keyed by viewer UUID and pruned when a player closes their menu or quits the server, so it stays bounded
 * to online players and does not grow without bound.
 */
final class ClickGuard {

    /** Default debounce window; a viewer click within this of their last accepted click is ignored. */
    static final Duration DEFAULT_WINDOW = Duration.ofMillis(150L);

    private final long windowMillis;
    private final Map<UUID, Long> lastAccepted = new ConcurrentHashMap<>();

    ClickGuard() {
        this(DEFAULT_WINDOW);
    }

    ClickGuard(Duration window) {
        this.windowMillis = Objects.requireNonNull(window, "window").toMillis();
    }

    /**
     * Whether {@code player}'s click should be accepted now. Returns {@code true} and records the time on
     * the first accept and again only once the window has elapsed; intervening clicks return {@code false}.
     */
    boolean accept(Player player) {
        Objects.requireNonNull(player, "player");
        return acceptAt(player.getUniqueId(), System.currentTimeMillis());
    }

    // Split out so a test can drive the clock without sleeping.
    boolean acceptAt(UUID viewer, long nowMillis) {
        Long previous = lastAccepted.get(viewer);
        if (previous != null && nowMillis - previous < windowMillis) {
            return false;
        }
        lastAccepted.put(viewer, nowMillis);
        return true;
    }

    /** Forget a viewer's last-click time, called when they close the menu so the table stays small. */
    void forget(UUID viewer) {
        lastAccepted.remove(Objects.requireNonNull(viewer, "viewer"));
    }
}

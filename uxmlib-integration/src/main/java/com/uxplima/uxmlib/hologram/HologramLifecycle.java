package com.uxplima.uxmlib.hologram;

import java.util.UUID;

/**
 * The per-widget lifecycle SPI. A hologram widget — a paged board, a leaderboard, a switchable view — keeps
 * per-player state (which page a player is on, what stat they picked) that must be reset when the player
 * joins, quits, respawns or changes world. Rather than each widget registering its own Bukkit listener, it
 * registers a {@code HologramLifecycle} with the {@link HologramManager}; the manager owns the single
 * listener and fans every player event out to each registered widget.
 *
 * <p>Every hook is a default no-op, so a widget overrides only the events it cares about. A hook receives the
 * player's {@link UUID} (never a {@code Player} reference) so a listener cannot pin a logged-out player. The
 * manager isolates each listener, so one that throws does not stop the others.
 *
 * @see HologramManager#registerLifecycle(HologramLifecycle)
 */
public interface HologramLifecycle {

    /** The player joined the server. */
    default void onJoin(UUID player) {}

    /** The player left the server — drop any per-player state held for it so it cannot leak. */
    default void onQuit(UUID player) {}

    /** The player changed world; cached visibility computed for the old world is no longer valid. */
    default void onWorldChange(UUID player) {}

    /** The player respawned, possibly somewhere else; re-evaluate anything position-dependent. */
    default void onRespawn(UUID player) {}
}

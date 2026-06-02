package com.uxplima.uxmlib.hologram;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Routes clicks on clickable holograms. A {@link ClickableHologram} spawns a native {@link Interaction}
 * entity sized to its text and registers its UUID here with a callback; the listeners match an interact
 * (RIGHT) or attack (LEFT) event to that UUID and fire the callback, debounced per player so a click is not
 * double-fired. Both paths are gated by {@link #withinReach}: a click whose player is in another world or
 * more than six blocks from the box is ignored, which blocks hacked-client reach. Register once with
 * {@link #install()} and spawn clickable holograms through {@link #clickable}.
 */
public final class HologramInteractions implements Listener {

    private static final long DEBOUNCE_MS = 150L;

    /**
     * Maximum squared distance (6 blocks) between a clicker and the interaction box for a click to count.
     * A vanilla client cannot interact past ~6 blocks; rejecting longer reaches blocks hacked-client clicks
     * and avoids firing on an interaction the player is nowhere near.
     */
    private static final double MAX_REACH_SQUARED = 36.0;

    private final Plugin plugin;
    private final Map<UUID, Consumer<HologramClick>> callbacks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public HologramInteractions(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Register the interact listener. Call once on enable. */
    public void install() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Spawn a {@link ClickableHologram} at {@code spec}'s location, wiring {@code onClick} to its clicks. */
    public ClickableHologram clickable(
            HologramSpec spec,
            org.bukkit.Location location,
            float width,
            float height,
            Consumer<HologramClick> onClick) {
        Objects.requireNonNull(onClick, "onClick");
        ClickableHologram hologram = ClickableHologram.spawn(spec, location, width, height);
        callbacks.put(hologram.interaction().getUniqueId(), onClick);
        return hologram;
    }

    /** Stop routing clicks for {@code hologram} (call when removing it). */
    public void forget(ClickableHologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        callbacks.remove(hologram.interaction().getUniqueId());
    }

    /**
     * Drop a departed player's debounce timestamp so {@link #lastClick} does not accumulate one stale entry
     * per player who ever clicked a hologram. Without this the map grows without bound on a high-churn server.
     */
    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Interaction interaction) {
            route(interaction, event.getPlayer(), HologramClick.Type.RIGHT);
        }
    }

    /**
     * The attack (LEFT) path. {@link PlayerInteractEntityEvent} only carries the right-click; a left-click on
     * an {@link Interaction} entity arrives as an {@link EntityDamageByEntityEvent} with a player damager.
     */
    @EventHandler
    void onLeftClick(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Interaction interaction && event.getDamager() instanceof Player player) {
            route(interaction, player, HologramClick.Type.LEFT);
        }
    }

    private void route(Interaction interaction, Player player, HologramClick.Type type) {
        Location playerLoc = player.getLocation();
        Location boxLoc = interaction.getLocation();
        if (playerLoc != null && boxLoc != null && withinReach(playerLoc, boxLoc)) {
            fire(interaction.getUniqueId(), new HologramClick(player, type));
        }
    }

    /**
     * Whether a click from {@code player} at the {@code interaction} box should count: same world and no
     * further than {@value #MAX_REACH_SQUARED} squared blocks apart. The world check comes first because
     * {@link Location#distanceSquared} throws across worlds. A null world on either side fails the gate.
     */
    static boolean withinReach(Location player, Location interaction) {
        World playerWorld = player.getWorld();
        World boxWorld = interaction.getWorld();
        if (playerWorld == null || boxWorld == null || !playerWorld.equals(boxWorld)) {
            return false;
        }
        return player.distanceSquared(interaction) <= MAX_REACH_SQUARED;
    }

    private void fire(UUID interactionId, HologramClick click) {
        Consumer<HologramClick> callback = callbacks.get(interactionId);
        if (callback != null && notDebounced(click.player().getUniqueId())) {
            callback.accept(click);
        }
    }

    private boolean notDebounced(UUID player) {
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(player, now);
        return previous == null || now - previous >= DEBOUNCE_MS;
    }

    // Test seam: the debounce map is otherwise only touched through native click events MockBukkit cannot
    // synthesise without a spawned Interaction entity.
    int trackedPlayerCount() {
        return lastClick.size();
    }

    void recordClickForTest(UUID player) {
        notDebounced(player);
    }
}

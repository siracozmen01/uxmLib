package com.uxplima.uxmlib.packet.item;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.packet.item.internal.NmsItemPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketPipeline;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Installs an {@link ItemView} on a server: the interceptor goes into every player's connection, the view
 * sits behind it, and the items that leave for a client go through it on the way out.
 *
 * <p>This is the whole plumbing a consumer would otherwise write again: a channel resolver, a listener
 * registry, the inject on join, the eject on quit, the players already online when the plugin enables, and
 * the delayed reorder pass that puts our handler back after the vanilla decoder when another plugin splices
 * ahead of it. {@code uxmlib-pipeline} deliberately leaves that choreography to its caller; this is the
 * caller for the item case.
 *
 * <p>The handler is named after the plugin, so two plugins that each install a view get one handler each and
 * neither can eject the other's.
 */
public final class ItemViews implements Listener {

    /** The prefix every handler this installs is named with, followed by the plugin's own name. */
    private static final String HANDLER_PREFIX = "uxmlib-item-view-";

    /**
     * How long after a join the handler's position is checked again. Long enough that the plugins which
     * splice their own handlers in on join have finished doing it, short enough that no player plays through
     * it.
     */
    private static final Duration REORDER_DELAY = Duration.ofSeconds(2);

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final PacketPipeline pipeline;
    private final PacketListenerRegistry registry = new PacketListenerRegistry();

    /**
     * Build the installer against the real server packets. Call {@link #install()} to switch it on.
     *
     * @param plugin the plugin the handler is named after and whose events carry the join and the quit
     * @param scheduler where the delayed reorder pass runs
     * @param view what every viewer is shown in place of the item the server holds
     */
    public ItemViews(Plugin plugin, Scheduler scheduler, ItemView view) {
        this(plugin, scheduler, new NmsItemPackets(), view);
    }

    /**
     * The same, against a supplied {@link ItemPackets}. This is the seam a test drives: the packet layer is
     * the only part that names the server internals, so a fake in its place makes the whole choreography
     * testable without a live server.
     */
    public ItemViews(Plugin plugin, Scheduler scheduler, ItemPackets packets, ItemView view) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(view, "view");
        this.registry.register(new ItemViewListener(packets, view));
        this.pipeline = new PacketPipeline(
                new ChannelResolver(),
                registry,
                handlerName(plugin),
                fault -> plugin.getSLF4JLogger().warn("An item view threw while drawing an item for a client.", fault));
    }

    private static String handlerName(Plugin plugin) {
        return HANDLER_PREFIX + plugin.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * Switch the view on: listen for joins and quits, and inject into everyone already online, which is what
     * makes the view work when a plugin is enabled while the server is running.
     */
    public void install() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            pipeline.inject(online);
        }
    }

    /** Switch it off again: stop listening, and take the handler back out of every open connection. */
    public void uninstall() {
        HandlerList.unregisterAll(this);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            pipeline.eject(online);
        }
    }

    /** Whether {@code player}'s connection currently carries the handler. */
    public boolean isInjected(Player player) {
        Objects.requireNonNull(player, "player");
        return pipeline.isInjected(player);
    }

    /** The pipeline handler name, which is what a server owner sees when they dump a connection. */
    public String handlerName() {
        return pipeline.handlerName();
    }

    /**
     * Inject on join at MONITOR, so every plugin that mutates the pipeline on the same event has already run
     * and our handler lands on top of what they built rather than under it.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void injectOnJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        pipeline.inject(player);
        scheduler.entityLater(player, REORDER_DELAY, () -> pipeline.reorder(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void ejectOnQuit(PlayerQuitEvent event) {
        pipeline.eject(event.getPlayer());
    }
}

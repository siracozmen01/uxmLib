package com.uxplima.uxmlib.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * A per-player screen history, so menus can push new screens and pop back to the previous one. Because
 * each screen is a retained {@link Gui} instance, popping reopens it exactly as the player left it — its
 * page, scroll offset, and per-viewer state are intact. Build multi-screen flows by pushing with
 * {@link #open} and wiring a {@link GuiItem#back} button to {@link #back}.
 *
 * <p>Register it once with {@link #install(Plugin)} so a player's stack is evicted when they disconnect;
 * otherwise call {@link #clear} yourself to avoid retaining stacks for players who have left.
 */
public final class GuiNavigator implements Listener {

    private final Map<UUID, Deque<Gui>> stacks = new ConcurrentHashMap<>();

    /** Register a quit listener so a disconnecting player's stack is evicted automatically. */
    public void install(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        stacks.remove(event.getPlayer().getUniqueId());
    }

    /** Push {@code gui} onto {@code viewer}'s stack and open it. */
    public void open(Player viewer, Gui gui) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(gui, "gui");
        stacks.computeIfAbsent(viewer.getUniqueId(), id -> new ArrayDeque<>()).push(gui);
        gui.open(viewer);
    }

    /** Replace {@code viewer}'s whole stack with {@code gui} as the new root and open it. */
    public void openRoot(Player viewer, Gui gui) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(gui, "gui");
        Deque<Gui> stack = new ArrayDeque<>();
        stack.push(gui);
        stacks.put(viewer.getUniqueId(), stack);
        gui.open(viewer);
    }

    /** Pop the current screen and reopen the previous one; returns false if there is nothing to go back to. */
    public boolean back(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        Deque<Gui> stack = stacks.get(viewer.getUniqueId());
        if (stack == null || stack.size() < 2) {
            return false;
        }
        stack.pop();
        Gui previous = stack.peek();
        if (previous != null) {
            previous.open(viewer);
        }
        return true;
    }

    /** Whether {@code viewer} has a previous screen to go back to. */
    public boolean canGoBack(Player viewer) {
        Deque<Gui> stack = stacks.get(viewer.getUniqueId());
        return stack != null && stack.size() >= 2;
    }

    /** The screen {@code viewer} is currently on, or {@code null} if their stack is empty. */
    public @org.jspecify.annotations.Nullable Gui current(Player viewer) {
        Deque<Gui> stack = stacks.get(viewer.getUniqueId());
        return stack == null ? null : stack.peek();
    }

    /** Forget {@code viewer}'s history (call when their final menu closes). */
    public void clear(Player viewer) {
        stacks.remove(viewer.getUniqueId());
    }
}

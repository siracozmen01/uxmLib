package com.uxplima.uxmlib.gui.input;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;

/**
 * One front door for asking a player to type a line of text, over three native backends — {@code ANVIL}
 * (the existing {@link AnvilInput}), {@code CHAT} (the player's next chat message), {@code SIGN} (a
 * transient sign) — all delivering the same {@link InputResult} through one callback. Per-player pending
 * state lives on the instance via an {@link InputRouter}; there is no static mutable state.
 *
 * <p>Construct one per plugin, {@link #install()} it once on enable, then {@link #open} as needed. A
 * configurable cancel keyword aborts any backend, and pending requests auto-clean on quit. When a
 * {@link Scheduler} is supplied, results from the async chat backend are marshalled back onto the player's
 * region thread before the callback runs, so a callback may safely touch the Bukkit API.
 */
public final class PlayerInput implements Listener {

    /** The keyword used when a caller does not specify one. */
    public static final String DEFAULT_CANCEL_KEYWORD = "cancel";

    private final Plugin plugin;
    private final @Nullable Scheduler scheduler;
    private final InputRouter router;
    private final AnvilInput anvil;
    private final SignPrompt signPrompt = new SignPrompt();

    public PlayerInput(Plugin plugin) {
        this(plugin, null, DEFAULT_CANCEL_KEYWORD);
    }

    public PlayerInput(Plugin plugin, @Nullable Scheduler scheduler, String cancelKeyword) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = scheduler;
        this.router = new InputRouter(Objects.requireNonNull(cancelKeyword, "cancelKeyword"));
        this.anvil = new AnvilInput(plugin);
    }

    /** Register this listener and the anvil backend. Call once, on enable. */
    public void install() {
        anvil.install();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ask {@code player} for a line of text via {@code type}. The {@code prompt} is the hint shown to the
     * player (the anvil left-slot name, the sign's first lines); {@code callback} fires once with the
     * submission or a cancellation.
     */
    public void open(Player player, InputType type, Component prompt, Consumer<InputResult> callback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(callback, "callback");
        switch (type) {
            case ANVIL -> openAnvil(player, prompt, callback);
            case CHAT -> openCapture(player, type, prompt, callback);
            case SIGN -> openSign(player, prompt, callback);
        }
    }

    private void openAnvil(Player player, Component prompt, Consumer<InputResult> callback) {
        router.register(player.getUniqueId(), InputType.ANVIL, callback);
        ItemStack promptItem = new ItemStack(org.bukkit.Material.PAPER);
        promptItem.editMeta(meta -> meta.displayName(prompt));
        anvil.open(player, promptItem, anvilResult -> finishAnvil(player, anvilResult));
    }

    private void finishAnvil(Player player, AnvilResult result) {
        // Route the anvil text through the same keyword/sanitize path the other backends use.
        if (result instanceof AnvilResult.Submitted submitted) {
            router.submit(player.getUniqueId(), submitted.text());
        } else {
            router.cancel(player.getUniqueId());
        }
    }

    private void openCapture(Player player, InputType type, Component prompt, Consumer<InputResult> callback) {
        router.register(player.getUniqueId(), type, callback);
        player.sendMessage(prompt);
    }

    private void openSign(Player player, Component prompt, Consumer<InputResult> callback) {
        router.register(player.getUniqueId(), InputType.SIGN, callback);
        try {
            signPrompt.open(player, prompt);
        } catch (RuntimeException failure) {
            // If the sign editor cannot be opened the caller must still hear back, so resolve as cancelled
            // and surface why instead of leaving the request dangling.
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not open sign input prompt", failure);
            signPrompt.restore(player);
            router.cancel(player.getUniqueId());
        }
    }

    /**
     * Resolve {@code player}'s pending chat request against {@code message}, returning whether a request was
     * consumed (in which case the caller cancels the chat event so it is not broadcast). Package-visible so
     * the routing is unit-testable without constructing a full chat event.
     */
    boolean handleChat(Player player, Component message) {
        if (router.awaiting(player.getUniqueId()).orElse(null) != InputType.CHAT) {
            return false;
        }
        String line = Text.plain(message);
        dispatchOnRegion(player, () -> router.submit(player.getUniqueId(), line));
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (handleChat(event.getPlayer(), event.message())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSign(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (router.awaiting(player.getUniqueId()).orElse(null) != InputType.SIGN) {
            return;
        }
        String joined = joinLines(event.lines());
        signPrompt.restore(player);
        router.submit(player.getUniqueId(), joined);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        router.cancel(event.getPlayer().getUniqueId());
    }

    private void dispatchOnRegion(Player player, Runnable task) {
        if (scheduler == null) {
            task.run();
        } else {
            scheduler.entity(player, task);
        }
    }

    private static String joinLines(List<Component> lines) {
        StringBuilder out = new StringBuilder();
        for (Component line : lines) {
            String text = Text.plain(line);
            if (!text.isBlank()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(text);
            }
        }
        return out.toString();
    }
}

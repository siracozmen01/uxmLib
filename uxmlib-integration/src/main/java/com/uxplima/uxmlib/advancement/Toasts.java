package com.uxplima.uxmlib.advancement;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Pops transient advancement toasts. The trick (mirrored from CMILib's {@code CMIAdvancement}, re-built on
 * the native Bukkit API) is to register a one-criterion synthetic advancement through the data-pack loader,
 * award that criterion to make the client animate the toast, then revoke and unregister it a couple of ticks
 * later so it never lingers in the player's advancement screen. No packets and no NMS — just
 * {@link org.bukkit.UnsafeValues#loadAdvancement} / {@link org.bukkit.UnsafeValues#removeAdvancement} and
 * {@link org.bukkit.advancement.AdvancementProgress}.
 *
 * <p>Constructor-injected with the owning {@link Plugin} (its name namespaces the synthetic keys) and the
 * library {@link Scheduler} (the delayed cleanup runs on the global region, never on a Bukkit scheduler).
 */
public final class Toasts {

    /** How long the synthetic advancement stays awarded before it is revoked and removed. */
    private static final Duration CLEANUP_DELAY = Duration.ofMillis(150L);

    private final Plugin plugin;
    private final Scheduler scheduler;

    // Per-instance counter so two toasts in the same tick get distinct keys; never static (no shared state).
    private final AtomicLong sequence = new AtomicLong();

    public Toasts(Plugin plugin, Scheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** A fluent builder already bound to this service, so {@code toasts.builder()...show(player)} works. */
    public Toast.Builder builder() {
        return new Toast.Builder(this);
    }

    /**
     * Pop {@code toast} for {@code player}. Registers the synthetic advancement, awards its criterion to fire
     * the toast, and schedules the revoke-and-remove cleanup. Must be called on the player's region thread
     * (the registration and award touch the live player); the cleanup is scheduled onto the global region.
     */
    @SuppressWarnings(
            "deprecation") // loadAdvancement/removeAdvancement are the only native, packet-free route on 1.21.
    public void show(Toast toast, Player player) {
        Objects.requireNonNull(toast, "toast");
        Objects.requireNonNull(player, "player");
        NamespacedKey key = nextKey();
        Bukkit.getUnsafe().loadAdvancement(key, toast.toJson());
        // Resolve through the server registry rather than the loader's return: loadAdvancement yields null
        // when the key already existed, while getAdvancement is the single source of truth once it is loaded.
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            removeQuietly(key);
            return;
        }
        Advancements.grant(player, advancement);
        scheduleCleanup(key, advancement, player);
    }

    private void scheduleCleanup(NamespacedKey key, Advancement advancement, Player player) {
        scheduler.globalLater(CLEANUP_DELAY, () -> {
            if (player.isOnline()) {
                Advancements.revoke(player, advancement);
            }
            removeQuietly(key);
        });
    }

    @SuppressWarnings("deprecation") // removeAdvancement is internal but the only native un-register on 1.21.
    private void removeQuietly(NamespacedKey key) {
        Bukkit.getUnsafe().removeAdvancement(key);
        // Removing an advancement needs a data reload to leave the server's advancement set consistent; the
        // recipe-update path is the cheap public refresh that also re-syncs the client's advancement state.
        Bukkit.updateRecipes();
    }

    private NamespacedKey nextKey() {
        return new NamespacedKey(plugin, "toast_" + sequence.getAndIncrement());
    }
}

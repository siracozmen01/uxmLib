package com.uxplima.uxmlib.hook.economy;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.OfflinePlayer;

import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * An async port over an {@link EconomyBridge}: balance reads and deposits/withdrawals can block on the
 * backing economy plugin, so this runs each call on {@link Scheduler#async} and hands back a
 * {@link CompletableFuture}. It mirrors the storage module's async port — work is submitted through the
 * injected {@code Scheduler}, never {@code CompletableFuture.supplyAsync} (which can silently fall back to
 * the common pool and is not Folia-correct). The future completes with the result, or exceptionally if the
 * underlying call throws, so failures propagate instead of being swallowed.
 */
public final class AsyncEconomy {

    private final EconomyBridge bridge;
    private final Scheduler scheduler;

    /** Wrap {@code bridge}, routing every async call through {@code scheduler}'s off-thread executor. */
    public AsyncEconomy(EconomyBridge bridge, Scheduler scheduler) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The player's balance, read off the main thread. */
    public CompletableFuture<Double> balanceAsync(OfflinePlayer player) {
        Objects.requireNonNull(player, "player");
        return on(() -> bridge.balance(player));
    }

    /** Whether the player has at least {@code amount}, checked off the main thread. */
    public CompletableFuture<Boolean> hasAsync(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return on(() -> bridge.has(player, amount));
    }

    /** Take {@code amount} from the player off the main thread; the future yields whether it succeeded. */
    public CompletableFuture<Boolean> withdrawAsync(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return on(() -> bridge.withdraw(player, amount));
    }

    /** Give {@code amount} to the player off the main thread; the future yields whether it succeeded. */
    public CompletableFuture<Boolean> depositAsync(OfflinePlayer player, double amount) {
        Objects.requireNonNull(player, "player");
        return on(() -> bridge.deposit(player, amount));
    }

    /** The wrapped bridge, for the synchronous accessors (format, currency names, isPresent). */
    public EconomyBridge bridge() {
        return bridge;
    }

    private <T> CompletableFuture<T> on(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.async(() -> {
            try {
                future.complete(work.get());
            } catch (RuntimeException failure) {
                future.completeExceptionally(failure);
            }
        });
        return future;
    }
}

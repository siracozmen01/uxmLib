package com.uxplima.uxmlib.hook.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link AsyncEconomy} with an inline scheduler so each future resolves on the test thread, proving
 * the port delegates to the wrapped bridge, completes with the result, and propagates a backend failure
 * exceptionally rather than swallowing it.
 */
class AsyncEconomyTest {

    private RecordingBridge bridge;
    private InlineScheduler scheduler;
    private AsyncEconomy economy;
    private OfflinePlayer player;

    @BeforeEach
    void setUp() {
        bridge = new RecordingBridge();
        scheduler = new InlineScheduler();
        economy = new AsyncEconomy(bridge, scheduler);
        player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000009"));
    }

    @Test
    void balanceAsyncReadsThroughTheBridgeOnTheScheduler() {
        CompletableFuture<Double> future = economy.balanceAsync(player);

        assertThat(future.join()).isEqualTo(42.0d);
        assertThat(scheduler.asyncCalls).isEqualTo(1);
        assertThat(bridge.balanceCalls).isEqualTo(1);
    }

    @Test
    void depositAndWithdrawDelegateAndReturnSuccess() {
        assertThat(economy.depositAsync(player, 10.0d).join()).isTrue();
        assertThat(economy.withdrawAsync(player, 5.0d).join()).isTrue();
        assertThat(economy.hasAsync(player, 1.0d).join()).isTrue();

        assertThat(bridge.lastDeposit).isEqualTo(10.0d);
        assertThat(bridge.lastWithdraw).isEqualTo(5.0d);
    }

    @Test
    void aBackendFailurePropagatesExceptionally() {
        bridge.failBalance = true;

        CompletableFuture<Double> future = economy.balanceAsync(player);

        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void exposesTheWrappedBridgeForSyncMetadata() {
        assertThat(economy.bridge()).isSameAs(bridge);
    }

    /** A bridge that records calls so the port's delegation can be asserted without a real economy plugin. */
    private static final class RecordingBridge implements EconomyBridge {
        int balanceCalls;
        double lastDeposit;
        double lastWithdraw;
        boolean failBalance;

        @Override
        public double balance(OfflinePlayer player) {
            balanceCalls++;
            if (failBalance) {
                throw new IllegalStateException("backend down");
            }
            return 42.0d;
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return true;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            lastWithdraw = amount;
            return true;
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            lastDeposit = amount;
            return true;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public String format(double amount) {
            return "$" + amount;
        }

        @Override
        public String currencySymbol() {
            return "Dollar";
        }

        @Override
        public String currencyNameSingular() {
            return "Dollar";
        }

        @Override
        public String currencyNamePlural() {
            return "Dollars";
        }
    }

    /** A scheduler that runs async work inline so the port's futures resolve synchronously in the test. */
    private static final class InlineScheduler implements Scheduler {
        int asyncCalls;

        private static TaskHandle noHandle() {
            return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }

        @Override
        public TaskHandle async(Runnable task) {
            asyncCalls++;
            task.run();
            return noHandle();
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            task.run();
            return noHandle();
        }

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            task.accept(noHandle());
            return noHandle();
        }

        @Override
        public TaskHandle global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalLater(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle region(Location location, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException();
        }
    }
}

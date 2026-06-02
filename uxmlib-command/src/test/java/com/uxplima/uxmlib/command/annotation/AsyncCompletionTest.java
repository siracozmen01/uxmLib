package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.junit.jupiter.api.Test;

/**
 * Covers the async-completion seam that backs item 35: detecting a {@link CompletableFuture}-returning
 * handler, and routing its completion back through the library {@code Scheduler} (global for the console,
 * the entity scheduler for a player) so the continuation never touches Bukkit off-thread. The routing is
 * driven with a synchronous {@code Scheduler} double that records which family ran the continuation, so the
 * behaviour is asserted without a live server (MockBukkit cannot dispatch a Brigadier tree).
 */
class AsyncCompletionTest {

    /** Records which scheduler family a continuation was routed onto and runs it inline. */
    static final class RecordingScheduler extends SameThreadScheduler {
        String routedTo = "none";

        @Override
        public TaskHandle global(Runnable task) {
            routedTo = "global";
            return super.global(task);
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            routedTo = "entity";
            return super.entity(entity, task);
        }
    }

    @Test
    void detectsAFutureReturnTypeAsAsync() {
        assertThat(AsyncCompletion.isAsync(CompletableFuture.class)).isTrue();
        assertThat(AsyncCompletion.isAsync(void.class)).isFalse();
        assertThat(AsyncCompletion.isAsync(String.class)).isFalse();
    }

    @Test
    void aSuccessfulFutureRunsNoErrorContinuation() {
        RecordingScheduler scheduler = new RecordingScheduler();
        java.util.List<Throwable> errors = new java.util.ArrayList<>();
        CompletableFuture<String> future = new CompletableFuture<>();

        AsyncCompletion.route(future, scheduler, consoleSource(), errors::add);
        future.complete("done");

        assertThat(errors).isEmpty();
        assertThat(scheduler.routedTo).isEqualTo("none");
    }

    @Test
    void anExceptionalFutureRoutesTheCauseOnTheConsoleGlobalThread() {
        RecordingScheduler scheduler = new RecordingScheduler();
        java.util.List<Throwable> errors = new java.util.ArrayList<>();
        CompletableFuture<String> future = new CompletableFuture<>();
        IllegalStateException boom = new IllegalStateException("boom");

        AsyncCompletion.route(future, scheduler, consoleSource(), errors::add);
        future.completeExceptionally(boom);

        // The CompletionException wrapper is peeled off; the continuation ran on the global region.
        assertThat(errors).containsExactly(boom);
        assertThat(scheduler.routedTo).isEqualTo("global");
    }

    @Test
    void anExceptionalFutureRoutesAPlayerOntoTheEntityThread() {
        RecordingScheduler scheduler = new RecordingScheduler();
        java.util.List<Throwable> errors = new java.util.ArrayList<>();
        CompletableFuture<String> future = new CompletableFuture<>();

        AsyncCompletion.route(future, scheduler, playerSource(), errors::add);
        future.completeExceptionally(new IllegalStateException("boom"));

        assertThat(scheduler.routedTo).isEqualTo("entity");
        assertThat(errors).hasSize(1);
    }

    @Test
    void anAlreadyFailedFutureStillRoutesThroughTheScheduler() {
        RecordingScheduler scheduler = new RecordingScheduler();
        java.util.List<Throwable> errors = new java.util.ArrayList<>();
        IllegalStateException boom = new IllegalStateException("late");

        AsyncCompletion.route(CompletableFuture.failedFuture(boom), scheduler, consoleSource(), errors::add);

        assertThat(errors).containsExactly(boom);
        assertThat(scheduler.routedTo).isEqualTo("global");
    }

    @Test
    void asFutureRecognisesAFutureAndRejectsOtherReturnValues() {
        CompletableFuture<String> future = CompletableFuture.completedFuture("x");
        assertThat(AsyncCompletion.asFuture(future)).isSameAs(future);
        assertThat(AsyncCompletion.asFuture("not a future")).isNull();
        assertThat(AsyncCompletion.asFuture(null)).isNull();
    }

    private static CommandSourceStack consoleSource() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(mock(CommandSender.class));
        return source;
    }

    private static CommandSourceStack playerSource() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(mock(Player.class));
        return source;
    }
}

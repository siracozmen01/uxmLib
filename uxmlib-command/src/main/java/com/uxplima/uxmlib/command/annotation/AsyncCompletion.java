package com.uxplima.uxmlib.command.annotation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * Routes the completion of an async command handler back onto a Bukkit-safe thread. When a {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Subcommand} method returns a {@link CompletableFuture}
 * the heavy work runs wherever the handler put it (typically {@link Scheduler#async}); the framework's only
 * job is to make <em>completion</em> safe — its continuation must never touch the Bukkit API off-thread. So
 * when the future settles we hop back through the library {@link Scheduler}: onto the region owning the
 * player sender ({@link Scheduler#entity}) when one ran the command, otherwise onto the global region
 * ({@link Scheduler#global}). A future that completes exceptionally is reported through the same clean-error
 * path a thrown handler uses, not as a Brigadier stacktrace.
 *
 * <p>Pattern inspired by Lamp's async return (MIT); the Scheduler hop and error routing are ours.
 */
final class AsyncCompletion {

    private AsyncCompletion() {}

    /**
     * Attach completion routing to {@code future}. On success the result is ignored (the handler owns its
     * side effects); on failure {@code onError} runs on the sender's thread with the unwrapped cause. A
     * future that is already complete still routes through the scheduler so the continuation is uniform.
     */
    static void route(
            CompletableFuture<?> future, Scheduler scheduler, CommandSourceStack source, Consumer<Throwable> onError) {
        // whenComplete returns a derived stage we don't chain on; the completion handling is the scheduler
        // hop below, so the returned stage is deliberately unused.
        var unused = future.whenComplete((result, error) -> {
            if (error != null) {
                onSenderThread(scheduler, source, () -> onError.accept(unwrap(error)));
            }
        });
    }

    /** Run {@code task} on the region thread owning the sender (entity for a player, global otherwise). */
    private static void onSenderThread(Scheduler scheduler, CommandSourceStack source, Runnable task) {
        CommandSender sender = source.getSender();
        if (sender instanceof Player player) {
            scheduler.entity(player, task);
        } else {
            scheduler.global(task);
        }
    }

    /** Peel the {@link CompletionException}/{@code ExecutionException} wrapper off a future failure. */
    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    /** Whether {@code returnType} is a {@link CompletableFuture}, i.e. the method is an async handler. */
    static boolean isAsync(Class<?> returnType) {
        return CompletableFuture.class.isAssignableFrom(returnType);
    }

    /** The future a handler returned, or {@code null} when it returned {@code null} (treated as done). */
    static @Nullable CompletableFuture<?> asFuture(@Nullable Object returnValue) {
        return returnValue instanceof CompletableFuture<?> future ? future : null;
    }
}

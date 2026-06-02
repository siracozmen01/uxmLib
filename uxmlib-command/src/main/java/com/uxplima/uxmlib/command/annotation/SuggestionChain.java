package com.uxplima.uxmlib.command.annotation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * Composes several {@link SuggestionSource}s into one ordered decline-chain: each is tried in turn and the
 * first that offers a non-empty set of completions wins; if it declines (an empty set) or fails, the next is
 * tried. Because each source returns a {@link CompletableFuture}, a source may compute off-thread and the
 * chain still threads the right result back to Brigadier without blocking. This generalises the fixed
 * {@code @SuggestWith} &gt; {@code @Suggest} &gt; resolver precedence {@link Suggestions} hard-codes into an
 * orderable, composable provider list. A source that completes exceptionally is treated as a decline, so one
 * flaky provider never blanks completion for the rest of the chain.
 */
final class SuggestionChain {

    private SuggestionChain() {}

    /** A single source that walks {@code sources} in order, the first non-empty result winning. */
    static SuggestionSource of(List<SuggestionSource> sources) {
        Objects.requireNonNull(sources, "sources");
        List<SuggestionSource> ordered = List.copyOf(sources);
        return (context, builder) -> walk(ordered, 0, context, builder);
    }

    private static CompletableFuture<Suggestions> walk(
            List<SuggestionSource> sources,
            int index,
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        if (index >= sources.size()) {
            return builder.buildFuture();
        }
        // Each source gets a fresh builder at the same cursor so an earlier source's offers don't bleed into
        // a later one when we fall through; only the winning source's result is returned.
        SuggestionsBuilder fresh = builder.restart();
        return safe(sources.get(index), context, fresh).thenCompose(result -> {
            if (!result.isEmpty()) {
                return CompletableFuture.completedFuture(result);
            }
            return walk(sources, index + 1, context, builder);
        });
    }

    /** Run one source, turning an exceptional completion into an empty (declining) result. */
    private static CompletableFuture<Suggestions> safe(
            SuggestionSource source, CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            return source.suggest(context, builder)
                    .exceptionally(failure -> Suggestions.empty().join());
        } catch (RuntimeException thrownSynchronously) {
            return CompletableFuture.completedFuture(Suggestions.empty().join());
        }
    }
}

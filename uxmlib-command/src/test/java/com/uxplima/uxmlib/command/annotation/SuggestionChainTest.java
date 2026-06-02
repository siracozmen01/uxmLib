package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Covers the ordered suggestion decline-chain: several providers are tried in order and the first that
 * offers a non-empty set wins; a provider may complete its future off-thread and the chain still feeds the
 * right result back. The chain is pure (it composes {@link SuggestionSource}s into one), so it is exercised
 * directly against a builder with no live server.
 */
class SuggestionChainTest {

    private static SuggestionsBuilder builder() {
        return new SuggestionsBuilder("", 0);
    }

    @SuppressWarnings("unchecked")
    private static CommandContext<CommandSourceStack> context() {
        return Mockito.mock(CommandContext.class);
    }

    private static SuggestionSource offering(String... values) {
        return (ctx, builder) -> {
            for (String value : values) {
                builder.suggest(value);
            }
            return builder.buildFuture();
        };
    }

    private static final SuggestionSource EMPTY = (ctx, builder) -> builder.buildFuture();

    @Test
    void firstNonEmptyProviderWins() {
        SuggestionSource chain = SuggestionChain.of(List.of(EMPTY, offering("apple", "banana"), offering("zzz")));
        Suggestions result = chain.suggest(context(), builder()).join();
        assertThat(result.getList()).extracting(s -> s.getText()).containsExactly("apple", "banana");
    }

    @Test
    void laterProviderIsSkippedOnceOneOffers() {
        SuggestionSource chain = SuggestionChain.of(List.of(offering("one"), offering("two")));
        Suggestions result = chain.suggest(context(), builder()).join();
        assertThat(result.getList()).extracting(s -> s.getText()).containsExactly("one");
    }

    @Test
    void anEmptyChainOffersNothing() {
        SuggestionSource chain = SuggestionChain.of(List.of(EMPTY, EMPTY));
        Suggestions result = chain.suggest(context(), builder()).join();
        assertThat(result.getList()).isEmpty();
    }

    @Test
    void anAsyncProviderCompletedOffThreadIsFedBack() {
        SuggestionSource async = (ctx, builder) -> CompletableFuture.supplyAsync(() -> {
            builder.suggest("remote");
            return builder.build();
        });
        SuggestionSource chain = SuggestionChain.of(List.of(EMPTY, async));
        Suggestions result = chain.suggest(context(), builder()).join();
        assertThat(result.getList()).extracting(s -> s.getText()).containsExactly("remote");
    }

    @Test
    void aFailingProviderDoesNotMaskTheNextOne() {
        SuggestionSource boom = (ctx, builder) ->
                CompletableFuture.failedFuture(new CompletionException(new IllegalStateException("down")));
        SuggestionSource chain = SuggestionChain.of(List.of(boom, offering("fallback")));
        Suggestions result = chain.suggest(context(), builder()).join();
        assertThat(result.getList()).extracting(s -> s.getText()).containsExactly("fallback");
    }
}

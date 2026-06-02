package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * Wires tab-completion onto an argument node as an ordered {@link SuggestionChain decline-chain}: an explicit
 * {@code @}{@link SuggestWith} provider is tried first, then a static {@code @}{@link Suggest} list, then the
 * resolver's own {@link ParamResolver#suggestions()} (e.g. an enum's constants); the first source that offers
 * a non-empty set wins and a source may compute off-thread. When none of these applies the node keeps the
 * argument type's native suggestions (a player or world arg completes itself), so a provider is attached only
 * when there is something to override with. Replacing the old mutually-exclusive precedence with a chain lets
 * a context-aware provider fall back to a static list, or a list fall back to the resolver's constants.
 */
final class Suggestions {

    private Suggestions() {}

    static void apply(
            RequiredArgumentBuilder<CommandSourceStack, ?> builder, Parameter parameter, ParamResolver<?> resolver) {
        List<SuggestionSource> sources = sourcesFor(parameter, resolver);
        if (sources.isEmpty()) {
            return;
        }
        SuggestionSource chain = SuggestionChain.of(sources);
        builder.suggests(chain::suggest);
    }

    /** The ordered suggestion sources for {@code parameter}: explicit provider, static list, then resolver. */
    private static List<SuggestionSource> sourcesFor(Parameter parameter, ParamResolver<?> resolver) {
        List<SuggestionSource> sources = new ArrayList<>();
        SuggestWith suggestWith = parameter.getAnnotation(SuggestWith.class);
        if (suggestWith != null) {
            sources.add(instantiate(suggestWith.value()));
        }
        Suggest suggest = parameter.getAnnotation(Suggest.class);
        if (suggest != null) {
            sources.add(fromList(List.of(suggest.value())));
        }
        Collection<String> fromResolver = resolver.suggestions();
        if (fromResolver != null) {
            sources.add(fromList(fromResolver));
        }
        return sources;
    }

    private static SuggestionSource instantiate(Class<? extends SuggestionSource> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new CommandParseException(
                    "@SuggestWith provider " + type.getName() + " needs a public no-arg constructor", failure);
        }
    }

    /** A source that offers each value whose lower-case form starts with what the player has typed. */
    private static SuggestionSource fromList(Collection<String> values) {
        return (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> {
            String remaining = builder.getRemainingLowerCase();
            for (String value : values) {
                if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(value);
                }
            }
            return builder.buildFuture();
        };
    }
}

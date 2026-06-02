package com.uxplima.uxmlib.command.annotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

/**
 * A single greedy trailing Brigadier argument that parses a branch's flags and switches. Rather than fight
 * Brigadier with a chained node per flag (which forces a fixed order) or a strip-then-parse pre-pass over
 * the raw line (which we do not own), the renderer ends a flagged branch with one {@code flags} node of this
 * type; here we tokenize the remaining input into {@code --name value} / {@code -x value} / {@code --switch}
 * / {@code -x} entries against the branch's known flags, position-independent. Unknown or malformed flags
 * raise a syntax error on the same clean path a bad argument uses, and {@link #listSuggestions} offers only
 * flags not yet present so completion never re-suggests one.
 *
 * <p>Pattern: Lamp models flags as part of the node IR (MIT); the greedy-arg tokenizer is ours, chosen to
 * stay native to Brigadier.
 */
final class FlagArgumentType implements ArgumentType<Flags> {

    private static final DynamicCommandExceptionType UNKNOWN_FLAG =
            new DynamicCommandExceptionType(name -> new LiteralMessage("Unknown flag: " + name));
    private static final DynamicCommandExceptionType MISSING_VALUE =
            new DynamicCommandExceptionType(name -> new LiteralMessage("Flag --" + name + " needs a value"));

    private final List<FlagModel> flags;
    private final Map<String, FlagModel> byName = new HashMap<>();
    private final Map<Character, FlagModel> byShorthand = new HashMap<>();

    FlagArgumentType(List<FlagModel> flags) {
        this.flags = List.copyOf(flags);
        for (FlagModel flag : this.flags) {
            byName.put(flag.name().toLowerCase(Locale.ROOT), flag);
            if (flag.shorthand() != 0) {
                byShorthand.put(flag.shorthand(), flag);
            }
        }
    }

    @Override
    public Flags parse(StringReader reader) throws CommandSyntaxException {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, Boolean> switches = new LinkedHashMap<>();
        List<String> tokens = tokenize(reader);
        for (int i = 0; i < tokens.size(); i++) {
            i = consume(reader, tokens, i, values, switches);
        }
        return new Flags(values, switches);
    }

    private int consume(
            StringReader reader,
            List<String> tokens,
            int index,
            Map<String, String> values,
            Map<String, Boolean> switches)
            throws CommandSyntaxException {
        Token token = parseToken(reader, tokens.get(index));
        FlagModel flag = lookup(reader, token);
        if (!flag.isValueFlag()) {
            switches.put(flag.name(), Boolean.TRUE);
            return index;
        }
        if (token.inlineValue != null) {
            values.put(flag.name(), token.inlineValue);
            return index;
        }
        if (index + 1 >= tokens.size()) {
            throw MISSING_VALUE.createWithContext(reader, flag.name());
        }
        values.put(flag.name(), tokens.get(index + 1));
        return index + 1;
    }

    private FlagModel lookup(StringReader reader, Token token) throws CommandSyntaxException {
        FlagModel flag = token.shortForm
                ? byShorthand.get(token.key.isEmpty() ? '\0' : token.key.charAt(0))
                : byName.get(token.key.toLowerCase(Locale.ROOT));
        if (flag == null || (token.shortForm && token.key.length() != 1)) {
            throw UNKNOWN_FLAG.createWithContext(reader, (token.shortForm ? "-" : "--") + token.key);
        }
        return flag;
    }

    /** Split the reader's remaining input into whitespace-separated tokens, advancing the cursor to the end. */
    private static List<String> tokenize(StringReader reader) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        while (reader.canRead()) {
            char c = reader.read();
            if (Character.isWhitespace(c)) {
                flush(tokens, current);
            } else {
                current.append(c);
            }
        }
        flush(tokens, current);
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static Token parseToken(StringReader reader, String raw) throws CommandSyntaxException {
        if (raw.startsWith("--")) {
            return splitInline(raw.substring(2), false);
        }
        if (raw.startsWith("-") && raw.length() > 1) {
            return splitInline(raw.substring(1), true);
        }
        throw UNKNOWN_FLAG.createWithContext(reader, raw);
    }

    private static Token splitInline(String body, boolean shortForm) {
        int eq = body.indexOf('=');
        if (eq >= 0) {
            return new Token(body.substring(0, eq), body.substring(eq + 1), shortForm);
        }
        return new Token(body, null, shortForm);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // The flags node is a single greedy trailing argument, so the builder's start sits at the beginning of
        // the whole flag blob (e.g. "--count 5 --si"). Re-base it onto the last token so an accepted suggestion
        // replaces only that token; otherwise Brigadier would insert at the blob start and clobber every flag
        // already typed.
        SuggestionsBuilder scoped = rebaseToLastToken(builder);
        String tail = scoped.getRemainingLowerCase();
        for (FlagModel flag : flags) {
            String option = "--" + flag.name();
            if (tail.isEmpty() || option.startsWith(tail)) {
                scoped.suggest(option);
            }
        }
        return scoped.buildFuture();
    }

    /** Offset the builder to the start of the last whitespace-separated token, so a suggestion replaces only it. */
    private static SuggestionsBuilder rebaseToLastToken(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        if (lastSpace < 0) {
            return builder;
        }
        return builder.createOffset(builder.getStart() + lastSpace + 1);
    }

    /** A parsed flag token: its key (name or shorthand chars), an inline {@code =value}, and whether short. */
    private record Token(String key, @org.jspecify.annotations.Nullable String inlineValue, boolean shortForm) {}
}

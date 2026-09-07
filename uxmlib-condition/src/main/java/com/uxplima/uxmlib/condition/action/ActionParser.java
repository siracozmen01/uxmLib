package com.uxplima.uxmlib.condition.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The pure parser turning one config action string into a {@link ParsedAction}. The grammar is a bracketed
 * prefix naming the {@link ActionType}, optionally followed by a payload:
 *
 * <pre>{@code
 *   [message] <green>Hello, %player_name%!
 *   [console] give %player_name% diamond 1
 *   [sound] minecraft:entity.player.levelup 1.0 1.2
 *   [close]
 * }</pre>
 *
 * <p>Parsing happens once, at load: the returned closure carries the static payload and only resolves
 * placeholders at run time. An unknown prefix, a missing {@code ]}, or a payload-less string for a type that
 * needs one all raise {@link IllegalArgumentException} with a message naming the offending input, so a config
 * loader can surface the line to the author.
 */
public final class ActionParser {

    private ActionParser() {}

    /** Parse one action string into its type, payload and closure. */
    public static ParsedAction parse(String line) {
        Objects.requireNonNull(line, "line");
        String trimmed = line.strip();
        if (!trimmed.startsWith("[")) {
            throw new IllegalArgumentException("action must start with a [prefix]: " + line);
        }
        int close = trimmed.indexOf(']');
        if (close < 0) {
            throw new IllegalArgumentException("action prefix is missing its closing ']': " + line);
        }
        String keyword = trimmed.substring(1, close).strip();
        ActionType type = ActionType.fromPrefix(keyword);
        String payload = trimmed.substring(close + 1).strip();
        requirePayload(type, payload, line);
        return new ParsedAction(type, payload, build(type, payload));
    }

    private static void requirePayload(ActionType type, String payload, String line) {
        if (type.payloadRequired() && payload.isEmpty()) {
            throw new IllegalArgumentException("action [" + type.prefix() + "] needs a payload: " + line);
        }
    }

    private static Action build(ActionType type, String payload) {
        return switch (type) {
            case MESSAGE -> Actions.message(payload);
            case BROADCAST -> Actions.broadcast(payload);
            case ACTIONBAR -> Actions.actionBar(payload);
            case TITLE -> Actions.title(payload);
            case CONSOLE -> Actions.console(payload);
            case PLAYER -> Actions.playerCommand(payload);
            case CLOSE -> Actions.close();
            case SOUND -> Actions.sound(parseSound(payload));
            case TAKE_MONEY -> Actions.takeMoney(parseMoneyCost(payload));
            case TAKE_ITEM -> Actions.takeItem(parseItemCost(payload));
        };
    }

    /**
     * Split a {@code [take-money]} payload into its currency and amount. One token is the amount alone, which
     * spends the wallet's default currency; two tokens name the currency first. A literal amount is checked
     * now so a typo is a load error rather than a run-time refusal an operator meets in production.
     */
    private static Actions.MoneyCost parseMoneyCost(String payload) {
        List<String> parts = tokenize(payload);
        Actions.MoneyCost cost =
                switch (parts.size()) {
                    case 1 -> new Actions.MoneyCost("", parts.get(0));
                    case 2 -> new Actions.MoneyCost(parts.get(0), parts.get(1));
                    default ->
                        throw new IllegalArgumentException(
                                "action [take-money] takes <amount> or <currency> <amount>, got: " + payload);
                };
        requirePositiveLiteral(cost.amountTemplate(), "take-money", payload);
        return cost;
    }

    /**
     * Split a {@code [take-item]} payload into its item and amount. One token is the item alone and costs
     * one; two tokens name the amount second. A literal amount is checked now for the same reason.
     */
    private static Actions.ItemCost parseItemCost(String payload) {
        List<String> parts = tokenize(payload);
        Actions.ItemCost cost =
                switch (parts.size()) {
                    case 1 -> new Actions.ItemCost(parts.get(0), "1");
                    case 2 -> new Actions.ItemCost(parts.get(0), parts.get(1));
                    default ->
                        throw new IllegalArgumentException(
                                "action [take-item] takes <item> or <item> <amount>, got: " + payload);
                };
        requirePositiveLiteral(cost.amountTemplate(), "take-item", payload);
        return cost;
    }

    // A template holding a placeholder can only be checked once it resolves, so only a literal is validated
    // here. A zero or negative literal cost is always a mistake: it reads as a price and charges nothing.
    private static void requirePositiveLiteral(String amountTemplate, String prefix, String payload) {
        if (amountTemplate.indexOf('%') >= 0) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountTemplate);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "action [" + prefix + "] amount is not a number in: " + payload, notANumber);
        }
        if (!(amount > 0)) {
            throw new IllegalArgumentException("action [" + prefix + "] amount must be above zero in: " + payload);
        }
    }

    /**
     * Split a {@code [sound]} payload into its key template and optional volume/pitch. Volume defaults to
     * {@code 1.0} and pitch to {@code 1.0}; a non-numeric volume or pitch is a parse error so a typo is caught
     * at load rather than swallowed at run time.
     */
    private static Actions.SoundSpec parseSound(String payload) {
        List<String> parts = tokenize(payload);
        String key = parts.get(0);
        float volume = parts.size() > 1 ? parseFloat(parts.get(1), "volume", payload) : 1.0f;
        float pitch = parts.size() > 2 ? parseFloat(parts.get(2), "pitch", payload) : 1.0f;
        return new Actions.SoundSpec(key, volume, pitch);
    }

    // A manual whitespace tokenizer rather than String.split, which Error Prone flags for surprising behaviour
    // on trailing empties; this collapses runs of whitespace and never yields an empty token.
    private static List<String> tokenize(String payload) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
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

    private static float parseFloat(String value, String field, String payload) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("sound " + field + " is not a number in: " + payload, notANumber);
        }
    }
}

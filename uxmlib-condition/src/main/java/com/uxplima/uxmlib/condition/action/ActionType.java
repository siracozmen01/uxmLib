package com.uxplima.uxmlib.condition.action;

import java.util.Objects;

/**
 * The kinds of action the {@link ActionParser} understands, each identified by the bracketed prefix that
 * opens its config string (for example {@code [message]} or {@code [sound]}). The prefix is matched
 * case-insensitively so {@code [Message]} and {@code [MESSAGE]} both parse.
 *
 * <p>Whether an action carries a payload after the prefix is fixed per type: {@link #CLOSE} takes none, every
 * other type takes the rest of the string as its payload (a MiniMessage template, a command, or a sound spec).
 */
public enum ActionType {

    /** Send a MiniMessage template to the context's target audience. */
    MESSAGE("message", true),

    /** Send a MiniMessage template to every player on the server (a broadcast). */
    BROADCAST("broadcast", true),

    /** Send a MiniMessage template to the target's action bar. */
    ACTIONBAR("actionbar", true),

    /** Show a MiniMessage template as a title to the target. */
    TITLE("title", true),

    /** Play a sound (an Adventure sound key) to the target. */
    SOUND("sound", true),

    /** Dispatch a command from the console sender. */
    CONSOLE("console", true),

    /** Dispatch a command as the target player. */
    PLAYER("player", true),

    /** Close the target player's open inventory. Takes no payload. */
    CLOSE("close", false),

    /** Take money from the target through the context's wallet. Payload: {@code [currency] <amount>}. */
    TAKE_MONEY("take-money", true),

    /** Take items from the target through the context's item store. Payload: {@code <item> [amount]}. */
    TAKE_ITEM("take-item", true);

    private final String prefix;
    private final boolean payloadRequired;

    ActionType(String prefix, boolean payloadRequired) {
        this.prefix = prefix;
        this.payloadRequired = payloadRequired;
    }

    /** The bracket-less keyword that opens this action's config string. */
    public String prefix() {
        return prefix;
    }

    /** Whether a non-empty payload must follow the prefix. */
    public boolean payloadRequired() {
        return payloadRequired;
    }

    /** Resolve a type from its keyword (case-insensitive), or throw if it is not a known prefix. */
    public static ActionType fromPrefix(String keyword) {
        Objects.requireNonNull(keyword, "keyword");
        for (ActionType type : values()) {
            if (type.prefix.equalsIgnoreCase(keyword)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown action prefix: [" + keyword + "]");
    }
}

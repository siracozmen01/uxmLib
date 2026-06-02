package com.uxplima.uxmlib.condition;

/**
 * What a {@link ConditionList} does when one of its conditions fails. The policy is attached per condition
 * (via {@link ConditionList.Entry}), so a list can, say, silently skip an optional check while still messaging
 * and stopping on a required one.
 */
public enum FailurePolicy {

    /**
     * Record the condition's failure message into the request's error sink and keep evaluating the rest of
     * the list. The default: collect every reason a request failed so the caller can show them all at once.
     */
    SEND_MESSAGE(true, false, false, false),

    /**
     * Record the message <em>and</em> raise the request's cancel flag, then keep evaluating. Use when a
     * failure should both inform the player and cancel the gated event.
     */
    CANCEL(true, true, false, false),

    /** Fail without recording any message and keep evaluating — a quiet, message-free required check. */
    SILENCE(false, false, false, false),

    /**
     * Record the message and stop evaluating the rest of the list immediately (short-circuit). Use for a
     * gate that makes any later check meaningless once it fails.
     */
    STOP_CHAIN(true, false, true, false),

    /**
     * Dispatch the entry's configured command list and keep evaluating, recording no message and not
     * cancelling on its own. The commands are {@code [console]}/{@code [player]} action strings parsed once
     * with the entry (see {@link ConditionList.Builder#runCommands}); they run through the request's command
     * sinks — the same {@code CommandSink} seam the action engine uses — so production routes the dispatch
     * through the library {@code Scheduler} rather than calling Bukkit on whatever thread evaluated the list.
     * Combine with {@link #CANCEL}-style intent by wiring a {@code [console]} command, or pair this entry with
     * a separate {@link #SEND_MESSAGE} one when both a message and a command are wanted.
     */
    RUN_COMMANDS(false, false, false, true);

    private final boolean recordsMessage;
    private final boolean cancels;
    private final boolean stopsChain;
    private final boolean dispatchesCommands;

    FailurePolicy(boolean recordsMessage, boolean cancels, boolean stopsChain, boolean dispatchesCommands) {
        this.recordsMessage = recordsMessage;
        this.cancels = cancels;
        this.stopsChain = stopsChain;
        this.dispatchesCommands = dispatchesCommands;
    }

    /** Whether a failure under this policy adds the condition's message to the error sink. */
    public boolean recordsMessage() {
        return recordsMessage;
    }

    /** Whether a failure under this policy raises the request's cancel flag. */
    public boolean cancels() {
        return cancels;
    }

    /** Whether a failure under this policy stops the rest of the list from being evaluated. */
    public boolean stopsChain() {
        return stopsChain;
    }

    /** Whether a failure under this policy dispatches the entry's configured command list. */
    public boolean dispatchesCommands() {
        return dispatchesCommands;
    }
}

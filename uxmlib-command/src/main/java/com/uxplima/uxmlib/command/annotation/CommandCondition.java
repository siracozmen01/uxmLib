package com.uxplima.uxmlib.command.annotation;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;

/**
 * A pre-execute gate. Before a {@code @}{@link com.uxplima.uxmlib.command.annotation.annotations.Subcommand}
 * method's arguments are bound and the handler is invoked, every registered condition is tested against the
 * dispatch; a condition vetoes execution by throwing a {@link CommandConditionException} carrying the
 * sender-facing reason. Conditions generalise the kinds of checks Brigadier's {@code requires} cannot
 * express (it only hides nodes by permission): "player only", "not on cooldown", "in the right world". A
 * {@code @}{@link com.uxplima.uxmlib.command.annotation.annotations.Permission} stays a Brigadier
 * {@code requires} so it also hides the node; conditions are for run-time gates that should explain
 * themselves. Register one on a {@link ParamResolvers} registry with
 * {@link ParamResolvers#condition(CommandCondition)}.
 */
@FunctionalInterface
public interface CommandCondition {

    /**
     * Test the dispatch in {@code context}. Return normally to allow execution; throw a
     * {@link CommandConditionException} to veto it with a message shown to the sender.
     */
    void test(CommandContext<CommandSourceStack> context);

    /**
     * Thrown by a {@link CommandCondition} to veto execution. Its message is sent to the command sender in
     * red, exactly like a rejected argument, instead of running the handler.
     */
    final class CommandConditionException extends RuntimeException {

        private final String reason;

        public CommandConditionException(String reason) {
            super(java.util.Objects.requireNonNull(reason, "reason"));
            this.reason = reason;
        }

        /** The sender-facing reason the condition vetoed execution; never {@code null}. */
        public String reason() {
            return reason;
        }
    }
}

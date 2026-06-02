package com.uxplima.uxmlib.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.condition.action.CommandSink;
import org.jspecify.annotations.Nullable;

/**
 * The one bundle a {@link Condition} is tested against. It carries everything a condition (and the {@link
 * ConditionList} that drives it) needs without each consumer wiring a bespoke parameter list:
 *
 * <ul>
 *   <li>the <b>subject</b> — an optional {@link Player} and an optional generic {@code actor} object, so a
 *       condition can run for a player, a non-player actor, or both;
 *   <li>the injected {@link OperandResolver} used to resolve placeholder operand templates;
 *   <li>an <b>error sink</b> — the mutable list of failure {@link Component}s a {@link ConditionList} flushes
 *       to when conditions fail;
 *   <li>a <b>cancellable</b> flag a failing condition's {@link FailurePolicy#CANCEL} policy can raise, which
 *       a caller reads back to cancel the event/action the conditions were gating;
 *   <li>two optional {@link CommandSink}s — one dispatching as the console, one as the subject player — that a
 *       {@link FailurePolicy#RUN_COMMANDS} entry runs its configured commands through. They default to {@link
 *       CommandSink#noop()}; production wires sinks that route the dispatch through the library {@code
 *       Scheduler} so a command never runs on the wrong thread.
 * </ul>
 *
 * <p>v1 keeps the subject bases minimal: a {@code Player} is enough. The generic actor is an {@code Object}
 * escape hatch so a non-player condition source can ride along without expanding the subject hierarchy yet.
 */
public final class ConditionRequest {

    private final @Nullable Player player;
    private final @Nullable Object actor;
    private final OperandResolver resolver;
    private final List<Component> errors;
    private final CommandSink consoleSink;
    private final CommandSink playerSink;
    private boolean cancelled;

    private ConditionRequest(Builder builder) {
        this.player = builder.player;
        this.actor = builder.actor;
        this.resolver = builder.resolver;
        this.errors = builder.errors;
        this.consoleSink = builder.consoleSink;
        this.playerSink = builder.playerSink;
    }

    /** Start a request builder with the resolver seam every placeholder condition needs. */
    public static Builder builder(OperandResolver resolver) {
        return new Builder(resolver);
    }

    /** A request for a player subject using the identity resolver (literal operands only). */
    public static ConditionRequest forPlayer(Player player) {
        Objects.requireNonNull(player, "player");
        return builder(OperandResolver.identity()).player(player).build();
    }

    /** The subject player, if this request has one. */
    public Optional<Player> player() {
        return Optional.ofNullable(player);
    }

    /** The generic actor, if this request carries one. */
    public Optional<Object> actor() {
        return Optional.ofNullable(actor);
    }

    /** The injected resolver for operand templates. */
    public OperandResolver resolver() {
        return resolver;
    }

    /** The sink a {@link FailurePolicy#RUN_COMMANDS} entry's {@code [console]} commands dispatch through. */
    public CommandSink consoleSink() {
        return consoleSink;
    }

    /** The sink a {@link FailurePolicy#RUN_COMMANDS} entry's {@code [player]} commands dispatch through. */
    public CommandSink playerSink() {
        return playerSink;
    }

    /** The live, mutable error sink. A condition adds its failure message here. */
    public List<Component> errors() {
        return errors;
    }

    /** Add a failure message to the sink. */
    public void addError(Component message) {
        Objects.requireNonNull(message, "message");
        errors.add(message);
    }

    /** Whether some failing condition has asked the gated event/action to be cancelled. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** Raise the cancellable flag. Once set it stays set for the life of the request. */
    public void cancel() {
        this.cancelled = true;
    }

    /** A builder so the optional subject parts stay readable at call sites. */
    public static final class Builder {

        private final OperandResolver resolver;
        private final List<Component> errors = new ArrayList<>();
        private @Nullable Player player;
        private @Nullable Object actor;
        private CommandSink consoleSink = CommandSink.noop();
        private CommandSink playerSink = CommandSink.noop();

        private Builder(OperandResolver resolver) {
            this.resolver = Objects.requireNonNull(resolver, "resolver");
        }

        /** Set the subject player. */
        public Builder player(Player player) {
            this.player = Objects.requireNonNull(player, "player");
            return this;
        }

        /** Set the generic actor. */
        public Builder actor(Object actor) {
            this.actor = Objects.requireNonNull(actor, "actor");
            return this;
        }

        /** Set the console command sink a {@link FailurePolicy#RUN_COMMANDS} entry dispatches through. */
        public Builder consoleSink(CommandSink consoleSink) {
            this.consoleSink = Objects.requireNonNull(consoleSink, "consoleSink");
            return this;
        }

        /** Set the player command sink a {@link FailurePolicy#RUN_COMMANDS} entry dispatches through. */
        public Builder playerSink(CommandSink playerSink) {
            this.playerSink = Objects.requireNonNull(playerSink, "playerSink");
            return this;
        }

        /** Build the immutable-subject request (the error sink and cancel flag remain mutable). */
        public ConditionRequest build() {
            return new ConditionRequest(this);
        }
    }
}

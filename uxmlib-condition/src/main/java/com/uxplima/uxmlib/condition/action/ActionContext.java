package com.uxplima.uxmlib.condition.action;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.condition.ItemStore;
import com.uxplima.uxmlib.condition.OperandResolver;
import com.uxplima.uxmlib.condition.Wallet;
import org.jspecify.annotations.Nullable;

/**
 * The bundle an {@link Action} runs against, the action-side mirror of the condition module's request. It
 * carries everything the parsed closures need to deliver their effect without each call site wiring a bespoke
 * parameter list:
 *
 * <ul>
 *   <li>the <b>target</b> {@link Audience}: where {@code [message]}/{@code [actionbar]}/{@code [title]}/
 *       {@code [sound]} land, and the optional subject {@link Player} a {@code [close]} acts on;
 *   <li>a <b>broadcast</b> {@link Audience}: every recipient a {@code [broadcast]} reaches (production passes
 *       the server);
 *   <li>two {@link CommandSink}s: one dispatching as the console, one as the target player;
 *   <li>the injected {@link OperandResolver}, reused from the condition module, that turns a placeholder
 *       template (such as {@code "%player_name%"}) into a concrete string against the subject player.
 * </ul>
 *
 * <p>Every part is optional with a safe default (an empty audience, a no-op command sink, the identity
 * resolver), so an action never has to null-check the context: an effect with no target is simply a no-op.
 */
public final class ActionContext {

    private final Audience target;
    private final Audience broadcast;
    private final @Nullable Player player;
    private final CommandSink consoleSink;
    private final CommandSink playerSink;
    private final OperandResolver resolver;
    private final Wallet wallet;
    private final ItemStore itemStore;

    private ActionContext(Builder builder) {
        this.target = builder.target;
        this.broadcast = builder.broadcast;
        this.player = builder.player;
        this.consoleSink = builder.consoleSink;
        this.playerSink = builder.playerSink;
        this.resolver = builder.resolver;
        this.wallet = builder.wallet;
        this.itemStore = builder.itemStore;
    }

    /** Start a context builder with the resolver seam every placeholder action needs. */
    public static Builder builder(OperandResolver resolver) {
        return new Builder(resolver);
    }

    /** The audience messages, sounds, titles and action bars are delivered to. */
    public Audience target() {
        return target;
    }

    /** The audience a {@code [broadcast]} action reaches. */
    public Audience broadcast() {
        return broadcast;
    }

    /** The subject player, if this context has one. */
    public Optional<Player> player() {
        return Optional.ofNullable(player);
    }

    /** The sink a {@code [console]} command action dispatches through. */
    public CommandSink consoleSink() {
        return consoleSink;
    }

    /** The sink a {@code [player]} command action dispatches through. */
    public CommandSink playerSink() {
        return playerSink;
    }

    /** The wallet a {@code [take-money]} action spends through; {@link Wallet#empty()} by default. */
    public Wallet wallet() {
        return wallet;
    }

    /** The store a {@code [take-item]} action consumes through; {@link ItemStore#empty()} by default. */
    public ItemStore itemStore() {
        return itemStore;
    }

    /**
     * Resolve a template string against the subject player through the injected resolver. Unlike the
     * condition module's single-operand use, an action template is a whole line, so the wired resolver is
     * expected to substitute every embedded {@code %placeholder%} (the shape a PlaceholderAPI bridge provides);
     * the identity resolver leaves the line untouched.
     */
    public String resolve(String template) {
        Objects.requireNonNull(template, "template");
        return resolver.resolve(player, template);
    }

    /** A builder so the optional parts stay readable at call sites. */
    public static final class Builder {

        private final OperandResolver resolver;
        private Audience target = Audience.empty();
        private Audience broadcast = Audience.empty();
        private @Nullable Player player;
        private CommandSink consoleSink = CommandSink.noop();
        private CommandSink playerSink = CommandSink.noop();
        private Wallet wallet = Wallet.empty();
        private ItemStore itemStore = ItemStore.empty();

        private Builder(OperandResolver resolver) {
            this.resolver = Objects.requireNonNull(resolver, "resolver");
        }

        /** Set the audience messages/sounds/titles/action bars are delivered to. */
        public Builder target(Audience target) {
            this.target = Objects.requireNonNull(target, "target");
            return this;
        }

        /** Set the audience a {@code [broadcast]} reaches. */
        public Builder broadcast(Audience broadcast) {
            this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
            return this;
        }

        /**
         * Set the subject player. By default the player also becomes the target audience (a player is an
         * {@code Audience}); pass an explicit {@link #target(Audience)} afterwards to override.
         */
        public Builder player(Player player) {
            this.player = Objects.requireNonNull(player, "player");
            this.target = player;
            return this;
        }

        /** Set the console command sink. */
        public Builder consoleSink(CommandSink consoleSink) {
            this.consoleSink = Objects.requireNonNull(consoleSink, "consoleSink");
            return this;
        }

        /** Set the player command sink. */
        public Builder playerSink(CommandSink playerSink) {
            this.playerSink = Objects.requireNonNull(playerSink, "playerSink");
            return this;
        }

        /** Set the wallet a {@code [take-money]} action spends through. */
        public Builder wallet(Wallet wallet) {
            this.wallet = Objects.requireNonNull(wallet, "wallet");
            return this;
        }

        /** Set the store a {@code [take-item]} action consumes through. */
        public Builder itemStore(ItemStore itemStore) {
            this.itemStore = Objects.requireNonNull(itemStore, "itemStore");
            return this;
        }

        /** Build the immutable context. */
        public ActionContext build() {
            return new ActionContext(this);
        }
    }
}

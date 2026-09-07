package com.uxplima.uxmlib.condition.wallet;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * How to talk to one economy plugin.
 *
 * <p>A dozen economy plugins do the same two things under a dozen sets of names, and only two of them
 * publish a library this one could compile against. Rather than a dozen coordinates in the build file,
 * half of which are gone or private, each economy is a description: which plugin, which class, which
 * methods, what the methods take and what they hand back. One reader talks to all of them.
 *
 * <p>The cost is honest and it is written here. A plugin that renames a method in a later version breaks
 * its own description, and {@link BridgedWallet} then reads zero and refuses every take rather than
 * failing the server. The gain is that a server which has none of these plugins carries none of their
 * code.
 *
 * @param pluginName the name in the other plugin's own plugin file, which is the present-guard
 * @param providerClass the class the calls go to, named as text so nothing loads it too early
 * @param access where the object that answers comes from
 * @param accessorName the chain of no-argument methods for {@link Access#STATIC}, such as {@code
 *     getInstance.getAPI}
 * @param balanceMethod the method that answers a balance
 * @param takeMethod the method that takes an amount away
 * @param argument what the methods want where the player goes
 * @param answer what a take hands back, and how a success is read out of it
 * @param pools whether this economy holds one balance or several, and how one is named
 * @param calls the three things a name and a method cannot say
 */
public record EconomyBinding(
        String pluginName,
        String providerClass,
        Access access,
        @Nullable String accessorName,
        String balanceMethod,
        String takeMethod,
        Argument argument,
        Answer answer,
        Pools pools,
        Calls calls) {

    /**
     * Whether this economy holds one balance or several, and how one of several is named.
     *
     * <p>A {@link com.uxplima.uxmlib.condition.Wallet} is asked for a currency on every call, so this is
     * what decides whether that currency reaches the plugin at all. It matters more than it looks: Vault's
     * two-argument {@code getBalance} takes a world and not a currency, so an economy that holds one pool
     * must never be handed the name of a second, and {@link Style#ONE} is what forbids it.
     *
     * @param style how a currency name reaches the economy
     * @param holderClass for {@link Style#BY_OBJECT}, the class that hands out currency objects
     * @param lookupMethod for {@link Style#BY_OBJECT}, its static method that takes a name
     */
    public record Pools(
            Style style,
            @Nullable String holderClass,
            @Nullable String lookupMethod) {

        /** How a currency name reaches the economy, or fails to. */
        public enum Style {

            /** One balance. Only the empty currency name is answered; any other name is unknown. */
            ONE,

            /** Several balances, each named by a string the calls carry. */
            BY_NAME,

            /** Several balances, each an object fetched by name before the call. */
            BY_OBJECT
        }

        public Pools {
            Objects.requireNonNull(style, "style");
            if (style == Style.BY_OBJECT && (holderClass == null || lookupMethod == null)) {
                throw new IllegalArgumentException("a currency that is an object needs a class and a lookup");
            }
        }

        /** One balance, which is what nearly every economy has. */
        public static Pools one() {
            return new Pools(Style.ONE, null, null);
        }

        /** Several balances, each named by a string. */
        public static Pools byName() {
            return new Pools(Style.BY_NAME, null, null);
        }

        /** Several balances, each an object {@code lookupMethod} on {@code holderClass} hands out by name. */
        public static Pools byObject(String holderClass, String lookupMethod) {
            return new Pools(
                    Style.BY_OBJECT,
                    Objects.requireNonNull(holderClass, "holderClass"),
                    Objects.requireNonNull(lookupMethod, "lookupMethod"));
        }
    }

    /**
     * The two things a plugin may want that a name and a method cannot say.
     *
     * <p>Neither is peculiar to one plugin. One adjust method where another plugin has a take, and an
     * economy that asks who is moving the money, are each the shape of a family of them.
     *
     * @param takeNegates whether a take is a give of a negative number
     * @param naming what the caller calls itself, for an economy that asks who is moving the money
     */
    public record Calls(boolean takeNegates, @Nullable String naming) {

        /** A plugin that needs neither: a take of its own, and no question about who is asking. */
        public static Calls simple() {
            return new Calls(false, null);
        }

        /** What the caller calls itself, for a plugin that asks. */
        public Optional<String> introduction() {
            return Optional.ofNullable(naming);
        }
    }

    /** Where the object that answers questions comes from. */
    public enum Access {

        /** Registered with the server's service manager, as Vault does. */
        SERVICE,

        /** A chain of no-argument static methods, such as {@code getInstance.getAPI}. */
        STATIC,

        /** The plugin instance itself. */
        PLUGIN,

        /** The methods are static on the class named, and there is no object to fetch first. */
        CLASS
    }

    /** What the methods take where the player goes. */
    public enum Argument {
        OFFLINE_PLAYER,
        PLAYER_ID,
        PLAYER_NAME
    }

    /** What a take hands back, and how a success is read out of it. */
    public enum Answer {

        /** A boolean: true is done, and false is a refusal the economy made for itself. */
        BOOLEAN,

        /** A Vault response object, which is asked {@code transactionSuccess}. */
        VAULT_RESPONSE,

        /**
         * Nothing. The call not failing is the success.
         *
         * <p>An economy that answers this way cannot refuse an overdraft, so {@link BridgedWallet} reads
         * the balance itself before it calls, and refuses the take before anything moves.
         */
        NOTHING
    }

    public EconomyBinding {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(providerClass, "providerClass");
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(balanceMethod, "balanceMethod");
        Objects.requireNonNull(takeMethod, "takeMethod");
        Objects.requireNonNull(argument, "argument");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(pools, "pools");
        Objects.requireNonNull(calls, "calls");
        if (access == Access.STATIC && accessorName == null) {
            throw new IllegalArgumentException("a static access needs the name of the method that answers");
        }
    }
}

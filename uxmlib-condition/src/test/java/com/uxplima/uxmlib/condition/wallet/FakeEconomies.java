package com.uxplima.uxmlib.condition.wallet;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Objects shaped like the economy plugins a binding describes.
 *
 * <p>A binding is reflection, so the only thing a test can prove is that the reader finds the methods it
 * was told about, puts the values where the signature wants them, and reads the answers correctly. These
 * are those methods, in the shapes the real plugins use.
 */
final class FakeEconomies {

    private FakeEconomies() {}

    /** The Vault shape: an offline player, a double, and a response object that says whether it worked. */
    public static final class VaultShaped {

        private double balance;

        VaultShaped(double balance) {
            this.balance = balance;
        }

        double balance() {
            return balance;
        }

        public double getBalance(Object player) {
            return balance;
        }

        /** Vault also has a two-argument reading, and the second argument is a world. */
        public double getBalance(Object player, String world) {
            return -1;
        }

        public Response withdrawPlayer(Object player, double amount) {
            if (amount > balance) {
                return new Response(false);
            }
            balance -= amount;
            return new Response(true);
        }
    }

    /** What Vault hands back. It is asked one question. */
    public static final class Response {

        private final boolean success;

        Response(boolean success) {
            this.success = success;
        }

        public boolean transactionSuccess() {
            return success;
        }
    }

    /** The PlayerPoints shape: an id, a whole number, and a plain boolean. */
    public static final class PointsShaped {

        private int points;

        PointsShaped(int points) {
            this.points = points;
        }

        int points() {
            return points;
        }

        public int look(UUID player) {
            return points;
        }

        public boolean take(UUID player, int amount) {
            if (amount > points) {
                return false;
            }
            points -= amount;
            return true;
        }
    }

    /**
     * The EcoBits shape: two static methods on a utility class, a currency object, a decimal, and no take at
     * all, because {@code adjustBalance} is a give and a take is a give of a negative number.
     *
     * <p>It is static because the real one is, so the reader has to call it on no object at all. The balance
     * is held in a field of the currency object, because a static class holds nothing itself.
     */
    public static final class UtilityShaped {

        private UtilityShaped() {}

        public static BigDecimal getBalance(Object player, Held currency) {
            return currency.balance;
        }

        /** It cannot refuse. Whatever it is told, it does, which is why the wallet has to read first. */
        public static void adjustBalance(Object player, Held currency, BigDecimal amount) {
            currency.balance = currency.balance.add(amount);
        }
    }

    /** A currency that is an object, fetched by its name. */
    public static final class Held {

        private static final Map<String, Held> BY_ID = new HashMap<>();

        private BigDecimal balance;

        private Held(BigDecimal balance) {
            this.balance = balance;
        }

        /** Put one currency of that name into the register, as the plugin's own file would. */
        static Held of(String id, String balance) {
            Held held = new Held(new BigDecimal(balance));
            BY_ID.put(id, held);
            return held;
        }

        BigDecimal balance() {
            return balance;
        }

        public static @Nullable Held getByID(String id) {
            return BY_ID.get(id);
        }
    }

    /**
     * The VaultUnlocked shape: the caller says who it is first, the account is an id, the amount is a
     * decimal, and the answer is the same response object the old Vault hands back.
     */
    public static final class UnlockedShaped {

        private BigDecimal balance;
        private String lastCaller = "";

        UnlockedShaped(String balance) {
            this.balance = new BigDecimal(balance);
        }

        BigDecimal balance() {
            return balance;
        }

        String lastCaller() {
            return lastCaller;
        }

        public BigDecimal getBalance(String pluginName, UUID account) {
            lastCaller = pluginName;
            return balance;
        }

        public Response withdraw(String pluginName, UUID account, BigDecimal amount) {
            lastCaller = pluginName;
            if (amount.compareTo(balance) > 0) {
                return new Response(false);
            }
            balance = balance.subtract(amount);
            return new Response(true);
        }
    }

    /** A plugin that is here but answers to other names, as a renamed later version would. */
    public static final class Renamed {

        public double someOtherName(Object player) {
            return 0;
        }
    }

    /**
     * A plugin that fails on every call.
     *
     * <p>Failing is the whole point of it, so the check that asks for {@code @DoNotCall} is turned off here:
     * nothing calls these by name, and the reader has to meet a method that throws.
     */
    @SuppressWarnings("DoNotCallSuggester")
    public static final class Broken {

        public double getBalance(Object player) {
            throw new IllegalStateException("its own fault");
        }

        public Response withdrawPlayer(Object player, double amount) {
            throw new IllegalStateException("its own fault");
        }
    }

    /**
     * A plugin that hides its API behind two static hops, as more than one real economy does.
     *
     * <p>{@code getInstance.getAPI} is the shape the reader has to follow: a static method that answers with
     * the plugin, and a method on that which answers with the object the calls go to.
     */
    public static final class Hidden {

        private static final Hidden ONE = new Hidden();

        private final PointsShaped api = new PointsShaped(7);

        public static Hidden getInstance() {
            return ONE;
        }

        public PointsShaped getAPI() {
            return api;
        }
    }

    /** A plugin whose static accessor answers with nothing, as one that is not started yet would. */
    public static final class NotStarted {

        public static @Nullable NotStarted getInstance() {
            return null;
        }
    }
}

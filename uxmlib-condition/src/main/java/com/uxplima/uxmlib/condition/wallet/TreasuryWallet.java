package com.uxplima.uxmlib.condition.wallet;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.Wallet;
import me.lokka30.treasury.api.economy.EconomyProvider;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;
import me.lokka30.treasury.api.economy.transaction.EconomyTransactionInitiator;
import org.jspecify.annotations.Nullable;

/**
 * A balance Treasury holds.
 *
 * <p>Every other economy here is reached through an {@link EconomyBinding}: the name of a class, the name
 * of two methods, and what they take. Treasury fits in no such description. It returns nothing and calls
 * back a subscriber instead, an account is a hop of its own before the balance is, and moving money needs
 * a currency object and an initiator that says who asked. So this economy is written out.
 *
 * <p>Every call waits for its answer with a bound on it, which is {@code waitFor}. A stalled economy costs
 * one purchase and never the server, and an operator whose economy sits on a slow database can give it
 * longer.
 *
 * <p>Nothing outside {@code TreasuryCalls} names a Treasury type, and that inner class is only ever
 * reached once the provider has been found, which happens only once the plugin manager has said Treasury
 * is here. A server without the plugin therefore loads no Treasury class at all: the balance reads zero,
 * every take is refused, and nothing is logged.
 *
 * <p>A take reads the balance first and refuses the whole cost before it moves anything, because Treasury
 * lets a balance go under nothing and a wallet that took more than a player has would be lending money it
 * never agreed to lend. One honest gap is left, and it is the reason {@code waitFor} is an operator's
 * choice: if Treasury takes the money and then fails to answer inside the bound, this reads the silence as
 * a refusal, so the caller delivers nothing while the money may already have gone. Nothing this side of
 * the call can tell the two apart.
 */
public final class TreasuryWallet implements Wallet {

    /** The plugin name, which is the present-guard. */
    public static final String PLUGIN = "Treasury";

    /** The service Treasury registers, named as text so nothing loads it before the plugin is found. */
    public static final String PROVIDER = "me.lokka30.treasury.api.economy.EconomyProvider";

    private final Supplier<Optional<Object>> provider;
    private final Duration waitFor;
    private final System.Logger log;

    private volatile @Nullable Object handle;

    /**
     * @param provider where the Treasury service comes from, empty when the plugin is not here
     * @param waitFor how long one call may take before this gives up on it
     */
    public TreasuryWallet(Supplier<Optional<Object>> provider, Duration waitFor, System.Logger log) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.waitFor = Objects.requireNonNull(waitFor, "waitFor");
        this.log = Objects.requireNonNull(log, "log");
        if (waitFor.isNegative() || waitFor.isZero()) {
            throw new IllegalArgumentException("a treasury wallet must wait longer than nothing");
        }
    }

    /** The wallet this server's Treasury answers for, asking the service manager for the provider. */
    public static TreasuryWallet ofServer(Duration waitFor, System.Logger log) {
        Objects.requireNonNull(log, "log");
        EconomyProviders providers = new ServerEconomyProviders(log);
        return new TreasuryWallet(() -> providers.service(PLUGIN, PROVIDER), waitFor, log);
    }

    /** The named currency of Treasury, or its primary one when the name is empty. Zero if neither is here. */
    @Override
    public double balance(@Nullable Player player, String currency) {
        Objects.requireNonNull(currency, "currency");
        if (player == null) {
            return 0;
        }
        Object treasury = treasury();
        if (treasury == null) {
            return 0;
        }
        try {
            BigDecimal has = TreasuryCalls.balance(treasury, player.getUniqueId(), currency.strip(), waitFor);
            return has == null ? 0 : has.doubleValue();
        } catch (RuntimeException | LinkageError unreachable) {
            log.log(System.Logger.Level.WARNING, "Treasury could not be read for " + player.getName(), unreachable);
            return 0;
        }
    }

    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0) {
            return true;
        }
        if (player == null) {
            return false;
        }
        Object treasury = treasury();
        if (treasury == null) {
            return false;
        }
        try {
            return TreasuryCalls.withdraw(
                    treasury, player.getUniqueId(), currency.strip(), BigDecimal.valueOf(amount), waitFor);
        } catch (RuntimeException | LinkageError unreachable) {
            log.log(System.Logger.Level.WARNING, "Treasury could not take money, so nothing was", unreachable);
            return false;
        }
    }

    /**
     * The Treasury service, as a plain object, resolved on first use and kept.
     *
     * <p>Every method asks this one first and gives up on a {@code null} answer before it reaches anything
     * that names a Treasury type. That is not a style: it is what keeps a server without the plugin from
     * ever loading one of its classes.
     */
    private @Nullable Object treasury() {
        Object known = handle;
        if (known != null) {
            return known;
        }
        Object found = provider.get().orElse(null);
        if (found != null) {
            handle = found;
        }
        return found;
    }

    /**
     * Everything that names a Treasury type.
     *
     * <p>It is a class of its own because the machine reads the names in a method the moment it runs that
     * method, whether the line that names them does anything or not. A guard in the same class as the call
     * is not a guard. This class is loaded on its first call, and its first call is behind the guard.
     */
    private static final class TreasuryCalls {

        private TreasuryCalls() {}

        /** The balance, or {@code null} when Treasury holds no such currency and no such account. */
        static @Nullable BigDecimal balance(Object provider, UUID who, String named, Duration waitFor) {
            if (!(provider instanceof EconomyProvider treasury)) {
                return null;
            }
            Currency currency = currency(treasury, named);
            if (currency == null) {
                return null;
            }
            PlayerAccount account = account(treasury, who, waitFor);
            if (account == null) {
                return null;
            }
            return answer(waitFor, subscriber -> account.retrieveBalance(currency, subscriber));
        }

        /** Take the whole amount, or take nothing at all and say so. */
        static boolean withdraw(Object provider, UUID who, String named, BigDecimal amount, Duration waitFor) {
            if (!(provider instanceof EconomyProvider treasury)) {
                return false;
            }
            Currency currency = currency(treasury, named);
            if (currency == null) {
                return false;
            }
            PlayerAccount account = account(treasury, who, waitFor);
            if (account == null) {
                return false;
            }
            BigDecimal has = answer(waitFor, subscriber -> account.retrieveBalance(currency, subscriber));
            if (has == null || has.compareTo(amount) < 0) {
                return false;
            }
            EconomyTransactionInitiator<?> asked =
                    EconomyTransactionInitiator.createInitiator(EconomyTransactionInitiator.Type.PLAYER, who);
            // The answer is the balance that is left. Nothing here reads it: what matters is that the call
            // answered at all, and a call that did not answer throws out of the wait and is read as a
            // refusal by the wallet above.
            TreasuryCalls.<BigDecimal>answer(
                    waitFor, subscriber -> account.withdrawBalance(amount, asked, currency, subscriber));
            return true;
        }

        /** The currency this call prices in: the one the caller named, or the primary one. */
        private static @Nullable Currency currency(EconomyProvider treasury, String named) {
            return named.isEmpty()
                    ? treasury.getPrimaryCurrency()
                    : treasury.findCurrency(named).orElse(null);
        }

        /** The account of one player, made when Treasury has none for them yet. */
        private static @Nullable PlayerAccount account(EconomyProvider treasury, UUID who, Duration waitFor) {
            Boolean known = answer(waitFor, subscriber -> treasury.hasPlayerAccount(who, subscriber));
            return Boolean.TRUE.equals(known)
                    ? answer(waitFor, subscriber -> treasury.retrievePlayerAccount(who, subscriber))
                    : answer(waitFor, subscriber -> treasury.createPlayerAccount(who, subscriber));
        }

        /**
         * Ask Treasury one thing and wait for the answer.
         *
         * <p>Treasury answers a subscriber rather than returning, so the call is turned into a future and
         * the future is waited on with a bound. An economy that never answers throws out of here, and the
         * wallet above reads that as nothing having happened.
         */
        private static <T> @Nullable T answer(Duration waitFor, Consumer<EconomySubscriber<T>> call) {
            CompletableFuture<T> asked = EconomySubscriber.asFuture(call);
            try {
                return asked.get(waitFor.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("the wait for Treasury was interrupted", stopped);
            } catch (ExecutionException | TimeoutException unanswered) {
                throw new IllegalStateException("Treasury did not answer in " + waitFor, unanswered);
            }
        }
    }
}

package com.uxplima.uxmlib.condition.wallet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.Wallet;
import org.jspecify.annotations.Nullable;

/**
 * One economy plugin, read through its {@link EconomyBinding}.
 *
 * <p>Nothing is resolved until the first question is asked, and what is resolved is kept. The plugin
 * manager is consulted before any class of that plugin is named, so a server without the economy loads
 * none of its code: the balance reads zero and every take is refused, which is what {@link Wallet} says an
 * unknown currency does.
 *
 * <p>A call that fails at run time is read as "it did not happen". That is the safe way round for money
 * that belongs to somebody else: the caller is told nothing was taken, and nothing was. A failure is also
 * written to the log, because it is a fault an operator has to see.
 *
 * <p>A take is never a part of a cost. Where the economy refuses an overdraft itself, its refusal is read
 * straight back. Where it cannot, because it answers {@link EconomyBinding.Answer#NOTHING}, the balance is
 * read first and the take is refused before the call is made.
 *
 * <p>Instances are safe to share between threads. Reads and writes may block on the economy plugin, so the
 * driver decides which thread asks; this class only answers.
 */
public final class BridgedWallet implements Wallet {

    /** Stands for "this economy holds one balance and wants no currency argument". */
    private static final Object ONE_POOL = new Object();

    private final EconomyBinding binding;
    private final EconomyProviders providers;
    private final PlayerArguments arguments;
    private final System.Logger log;

    /** The object the calls go to, resolved on first use and kept. */
    private volatile @Nullable Object handle;

    /** The two methods per currency name, resolved on first use of that name and kept. */
    private final Map<String, Optional<Bound>> pools = new ConcurrentHashMap<>();

    private final AtomicBoolean warned = new AtomicBoolean();

    public BridgedWallet(
            EconomyBinding binding, EconomyProviders providers, PlayerArguments arguments, System.Logger log) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The wallet this server's economy answers for, asking the server for the object behind it. */
    public static BridgedWallet ofServer(EconomyBinding binding, System.Logger log) {
        return new BridgedWallet(binding, new ServerEconomyProviders(log), PlayerArguments.ofPlayer(), log);
    }

    /** The description this wallet reads its economy through. */
    public EconomyBinding binding() {
        return binding;
    }

    @Override
    public double balance(@Nullable Player player, String currency) {
        Objects.requireNonNull(currency, "currency");
        if (player == null) {
            return 0;
        }
        Optional<Bound> found = bound(currency);
        if (found.isEmpty()) {
            return 0;
        }
        Called answered = invoke(found.get().balance(), player, null);
        return answered != null && answered.value() instanceof Number number ? number.doubleValue() : 0;
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
        Optional<Bound> found = bound(currency);
        if (found.isEmpty()) {
            return false;
        }
        Call take = found.get().take();
        // A plugin with one adjust method and no take is given a negative number. It is the same move,
        // written the way that plugin writes it, and the sign is put on here and nowhere else.
        Object written = number(amount, take.amountType(), binding.calls().takeNegates());
        if (written == null) {
            // An economy that counts in whole numbers cannot be paid a fraction, and no economy can be
            // paid more than its own numbers hold. Both refuse here, before anything moves.
            return false;
        }
        if (binding.answer() == EconomyBinding.Answer.NOTHING && balance(player, currency) < amount) {
            // This economy cannot refuse an overdraft, so the refusal is made here. Reading the balance
            // first is the whole of the promise that a take is never a part of a cost.
            return false;
        }
        Called answered = invoke(take, player, written);
        if (answered == null) {
            return false;
        }
        return switch (binding.answer()) {
            case BOOLEAN -> Boolean.TRUE.equals(answered.value());
            case NOTHING -> true;
            case VAULT_RESPONSE -> succeeded(answered.value());
        };
    }

    /** The object the calls go to. It is asked for once and kept, and an absent plugin is asked again. */
    private Optional<Object> provider() {
        Object known = handle;
        if (known != null) {
            return Optional.of(known);
        }
        Optional<Object> found = providers.provider(binding);
        found.ifPresent(object -> handle = object);
        return found;
    }

    /** The two methods for one currency name, resolved once and kept. */
    private Optional<Bound> bound(String currency) {
        Optional<Object> found = provider();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Object object = found.get();
        return pools.computeIfAbsent(currency, named -> resolve(object, named));
    }

    private Optional<Bound> resolve(Object object, String currency) {
        Optional<Object> pool = pool(currency);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        Object named = pool.get() == ONE_POOL ? null : pool.get();
        Optional<Call> balance = call(object, binding.balanceMethod(), false, named);
        Optional<Call> take = call(object, binding.takeMethod(), true, named);
        if (balance.isEmpty() || take.isEmpty()) {
            if (warned.compareAndSet(false, true)) {
                log.log(
                        System.Logger.Level.WARNING,
                        "The {0} economy is here, but its methods are not the ones this version expects."
                                + " Nothing is read from it and nothing is taken.",
                        binding.pluginName());
            }
            return Optional.empty();
        }
        return Optional.of(new Bound(balance.get(), take.get()));
    }

    /**
     * What goes in the currency place of a call, or nothing when this economy holds no such currency.
     *
     * <p>An economy of one balance is only ever asked for the empty name. Handing it a second name would
     * be worse than useless: Vault's two-argument {@code getBalance} takes a world, so the name of a
     * currency it does not have would be read as the name of a world it does not have.
     */
    private Optional<Object> pool(String currency) {
        return switch (binding.pools().style()) {
            case ONE -> currency.isEmpty() ? Optional.of(ONE_POOL) : Optional.empty();
            case BY_NAME -> currency.isEmpty() ? Optional.empty() : Optional.of(currency);
            case BY_OBJECT -> currency.isEmpty() ? Optional.empty() : poolObject(currency);
        };
    }

    /** The currency as the plugin holds it, for a plugin that takes an object rather than a name. */
    private Optional<Object> poolObject(String currency) {
        EconomyBinding.Pools held = binding.pools();
        try {
            Class<?> holder = Class.forName(Objects.requireNonNull(held.holderClass()));
            Method lookup = holder.getMethod(Objects.requireNonNull(held.lookupMethod()), String.class);
            return Optional.ofNullable(lookup.invoke(null, currency));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unknown) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The " + binding.pluginName() + " economy has no currency called " + currency,
                    unknown);
            return Optional.empty();
        }
    }

    /** A Vault response says so itself. Anything else that answers that way is read the same. */
    private boolean succeeded(@Nullable Object response) {
        if (response == null) {
            return false;
        }
        try {
            Method asked = response.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(asked.invoke(response));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
            log.log(System.Logger.Level.WARNING, "Cannot read the answer of " + binding.pluginName(), unreadable);
            return false;
        }
    }

    /** One call, or {@code null} when the call itself failed and nothing can be said about the money. */
    private @Nullable Called invoke(Call call, Player player, @Nullable Object amount) {
        Object[] parameters = new Object[call.method().getParameterCount()];
        parameters[call.playerAt()] = arguments.of(binding.argument(), player);
        if (call.amountAt() >= 0 && amount != null) {
            parameters[call.amountAt()] = amount;
        }
        if (call.poolAt() >= 0) {
            parameters[call.poolAt()] = call.pool();
        }
        if (call.namingAt() >= 0) {
            parameters[call.namingAt()] = call.naming();
        }
        Object target = binding.access() == EconomyBinding.Access.CLASS ? null : handle;
        try {
            return new Called(call.method().invoke(target, parameters));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError failure) {
            // Read as "it did not happen". Nothing is delivered and nothing is lost, which is the safe way
            // round for money that belongs to somebody else.
            log.log(System.Logger.Level.WARNING, "The " + binding.pluginName() + " economy failed a call", failure);
            return null;
        }
    }

    /** The one method of that name whose parameters the three values fit. */
    private Optional<Call> call(Object object, String name, boolean withAmount, @Nullable Object pool) {
        Optional<String> naming = binding.calls().introduction();
        int wanted = 1 + (withAmount ? 1 : 0) + (pool != null ? 1 : 0) + (naming.isPresent() ? 1 : 0);
        Class<?> owner = object instanceof Class<?> type ? type : object.getClass();
        for (Method candidate : owner.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != wanted) {
                continue;
            }
            Optional<Call> fitted = fit(candidate, withAmount, pool, naming.orElse(null));
            if (fitted.isPresent()) {
                return fitted;
            }
        }
        return Optional.empty();
    }

    /**
     * Where each value goes in this signature.
     *
     * <p>Two plugins that take the same values order them differently, so the order is read off the
     * signature once rather than written down by an operator. The amount takes the first parameter that is
     * a number. The currency takes the last parameter it can go in, which is what separates a currency name
     * from a player name when both are text. The name the caller goes by takes the first text parameter
     * that is left, because an economy that asks who is moving the money asks it first. The player takes
     * what is left. A signature the values do not fit is not this method.
     */
    private static Optional<Call> fit(
            Method candidate, boolean withAmount, @Nullable Object pool, @Nullable String naming) {
        Class<?>[] parameters = candidate.getParameterTypes();
        boolean[] taken = new boolean[parameters.length];

        int amountAt = -1;
        if (withAmount) {
            amountAt = first(parameters, taken, BridgedWallet::isNumber);
            if (amountAt < 0) {
                return Optional.empty();
            }
            taken[amountAt] = true;
        }

        int poolAt = -1;
        if (pool != null) {
            poolAt = last(parameters, taken, type -> accepts(type, pool));
            if (poolAt < 0) {
                return Optional.empty();
            }
            taken[poolAt] = true;
        }

        int namingAt = -1;
        if (naming != null) {
            namingAt = first(parameters, taken, type -> type.isAssignableFrom(String.class));
            if (namingAt < 0) {
                return Optional.empty();
            }
            taken[namingAt] = true;
        }

        int playerAt = first(parameters, taken, type -> true);
        if (playerAt < 0) {
            return Optional.empty();
        }
        return Optional.of(new Call(candidate, playerAt, amountAt, poolAt, pool, namingAt, naming));
    }

    private static int first(Class<?>[] parameters, boolean[] taken, Predicate<Class<?>> wanted) {
        for (int at = 0; at < parameters.length; at++) {
            if (!taken[at] && wanted.test(parameters[at])) {
                return at;
            }
        }
        return -1;
    }

    private static int last(Class<?>[] parameters, boolean[] taken, Predicate<Class<?>> wanted) {
        for (int at = parameters.length - 1; at >= 0; at--) {
            if (!taken[at] && wanted.test(parameters[at])) {
                return at;
            }
        }
        return -1;
    }

    private static boolean isNumber(Class<?> type) {
        return type == int.class
                || type == long.class
                || type == short.class
                || type == byte.class
                || type == float.class
                || type == double.class
                || Number.class.isAssignableFrom(type);
    }

    private static boolean accepts(Class<?> type, Object value) {
        return !type.isPrimitive() && type.isInstance(value);
    }

    /**
     * The amount in the shape the parameter asks for, or {@code null} when this economy cannot hold it.
     *
     * <p>An economy that counts in whole numbers cannot be paid a fraction, and no economy can be paid a
     * sum larger than its own numbers hold. Both answer the same way, because the caller does the same
     * thing with either: it refuses the take and nothing moves.
     */
    private static @Nullable Object number(double amount, Class<?> type, boolean negated) {
        BigDecimal written = BigDecimal.valueOf(negated ? -amount : amount);
        if (type.isAssignableFrom(BigDecimal.class)) {
            return written;
        }
        try {
            if (type == long.class || type == Long.class) {
                return written.longValueExact();
            }
            if (type == int.class || type == Integer.class) {
                return written.intValueExact();
            }
            if (type == short.class || type == Short.class) {
                return written.shortValueExact();
            }
        } catch (ArithmeticException cannotHoldIt) {
            return null;
        }
        if (type == float.class || type == Float.class) {
            return (float) written.doubleValue();
        }
        return written.doubleValue();
    }

    /** What one call answered. A call that failed is a {@code null} {@link Called}, not a null value. */
    private record Called(@Nullable Object value) {}

    /** The two methods for one currency name. */
    private record Bound(Call balance, Call take) {}

    /** One resolved method, and where each value goes in it. */
    private record Call(
            Method method,
            int playerAt,
            int amountAt,
            int poolAt,
            @Nullable Object pool,
            int namingAt,
            @Nullable String naming) {

        /** The type the amount goes in as, so nothing of the amount is lost on the way in. */
        Class<?> amountType() {
            return amountAt < 0 ? double.class : method.getParameterTypes()[amountAt];
        }
    }
}

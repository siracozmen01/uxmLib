package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import me.lokka30.treasury.api.economy.EconomyProvider;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.currency.Currency;
import me.lokka30.treasury.api.economy.response.EconomySubscriber;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** The one economy the wallet seam is written out for, against an economy shaped like Treasury. */
class TreasuryWalletTest {

    private static final System.Logger LOG = System.getLogger(TreasuryWalletTest.class.getName());
    private static final Duration SOON = Duration.ofSeconds(2);

    private ServerMock server;
    private Player ada;

    @BeforeEach
    void startTheServer() {
        server = MockBukkit.mock();
        ada = server.addPlayer("Ada");
    }

    @AfterEach
    void stopTheServer() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a server without Treasury reads zero and takes nothing, and never fails")
    void staysQuietWhenTreasuryIsAbsent() {
        TreasuryWallet wallet = new TreasuryWallet(Optional::empty, SOON, LOG);

        assertThat(wallet.balance(ada, "gems")).isZero();
        assertThat(wallet.withdraw(ada, "gems", 1)).isFalse();
    }

    @Test
    @DisplayName("a wallet built from the server finds nothing when the plugin is not installed")
    void staysQuietWhenTheServerHasNoTreasuryPlugin() {
        TreasuryWallet wallet = TreasuryWallet.ofServer(SOON, LOG);

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a currency Treasury does not hold reads zero and takes nothing")
    void staysQuietWhenTheCurrencyIsUnknown() {
        FakeTreasury treasury = new FakeTreasury("coins", true);
        treasury.holds(ada.getUniqueId(), "250");
        TreasuryWallet wallet = wallet(treasury);

        assertThat(wallet.balance(ada, "gems")).isZero();
        assertThat(wallet.withdraw(ada, "gems", 1)).isFalse();
    }

    @Test
    @DisplayName("the balance is read through the account")
    void readsTheBalance() {
        FakeTreasury treasury = new FakeTreasury("gems", true);
        treasury.holds(ada.getUniqueId(), "250");

        assertThat(wallet(treasury).balance(ada, "gems")).isEqualTo(250);
    }

    @Test
    @DisplayName("a currency name that is nothing but spaces still means the primary currency")
    void readsThePrimaryCurrencyForAnEmptyName() {
        FakeTreasury treasury = new FakeTreasury("coins", true);
        treasury.holds(ada.getUniqueId(), "5");

        assertThat(wallet(treasury).balance(ada, "  ")).isEqualTo(5);
    }

    @Test
    @DisplayName("a player nobody wrote a row for gets one, and reads a balance of nothing")
    void makesAnAccountForAnewPlayer() {
        FakeTreasury treasury = new FakeTreasury("gems", true);

        assertThat(wallet(treasury).balance(ada, "gems")).isZero();
        assertThat(treasury.made).containsExactly(ada.getUniqueId());
    }

    @Test
    @DisplayName("money leaves the account, and the balance says so")
    void movesMoney() {
        FakeTreasury treasury = new FakeTreasury("gems", true);
        treasury.holds(ada.getUniqueId(), "100");
        TreasuryWallet wallet = wallet(treasury);

        assertThat(wallet.withdraw(ada, "gems", 40)).isTrue();
        assertThat(wallet.balance(ada, "gems")).isEqualTo(60);
    }

    @Test
    @DisplayName("more than a player has is not taken, and nothing moves")
    void refusesToTakeMoreThanThereIs() {
        FakeTreasury treasury = new FakeTreasury("gems", true);
        treasury.holds(ada.getUniqueId(), "10");
        TreasuryWallet wallet = wallet(treasury);

        // Treasury lets a balance go under nothing, so the whole cost is read before any of it is taken.
        assertThat(wallet.withdraw(ada, "gems", 11)).isFalse();
        assertThat(wallet.balance(ada, "gems")).isEqualTo(10);
    }

    @Test
    @DisplayName("an economy that never answers costs one take, and never the server")
    void givesUpOnAneconomyThatNeverAnswers() {
        FakeTreasury silent = new FakeTreasury("gems", false);
        silent.holds(ada.getUniqueId(), "100");
        TreasuryWallet wallet = new TreasuryWallet(() -> Optional.of(silent.provider()), Duration.ofMillis(50), LOG);

        assertThat(wallet.balance(ada, "gems")).isZero();
        assertThat(wallet.withdraw(ada, "gems", 1)).isFalse();
    }

    @Test
    @DisplayName("nobody is not a player, and a take of nothing succeeds without asking Treasury")
    void answersForNoPlayerAndForNoAmount() {
        FakeTreasury treasury = new FakeTreasury("gems", true);
        treasury.holds(ada.getUniqueId(), "100");
        TreasuryWallet wallet = wallet(treasury);

        assertThat(wallet.balance(null, "gems")).isZero();
        assertThat(wallet.withdraw(null, "gems", 1)).isFalse();
        assertThat(wallet.withdraw(ada, "gems", 0)).isTrue();
        assertThat(wallet.balance(ada, "gems")).isEqualTo(100);
    }

    @Test
    @DisplayName("a wallet that may wait for nothing is a wallet that never answers")
    void refusesAwaitOfNothing() {
        assertThatThrownBy(() -> new TreasuryWallet(Optional::empty, Duration.ZERO, LOG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TreasuryWallet wallet(FakeTreasury treasury) {
        return new TreasuryWallet(() -> Optional.of(treasury.provider()), SOON, LOG);
    }

    /**
     * An economy shaped like Treasury.
     *
     * <p>It is made of proxies, because the two interfaces the wallet touches carry thirty methods between
     * them and it calls six. A method nobody expected refuses loudly rather than answering something made
     * up.
     */
    private static final class FakeTreasury {

        private final Map<UUID, BigDecimal> balances = new HashMap<>();
        private final List<UUID> made = new ArrayList<>();
        private final String currencyId;
        private final boolean answers;

        FakeTreasury(String currencyId, boolean answers) {
            this.currencyId = currencyId;
            this.answers = answers;
        }

        void holds(UUID who, String amount) {
            balances.put(who, new BigDecimal(amount));
        }

        EconomyProvider provider() {
            return proxy(EconomyProvider.class, (method, arguments) -> switch (method) {
                case "getPrimaryCurrency" -> currency();
                case "findCurrency" -> currencyId.equals(arguments[0]) ? Optional.of(currency()) : Optional.empty();
                case "hasPlayerAccount" -> answer(arguments[1], balances.containsKey((UUID) arguments[0]));
                case "retrievePlayerAccount" -> answer(arguments[1], account((UUID) arguments[0]));
                case "createPlayerAccount" -> {
                    made.add((UUID) arguments[0]);
                    balances.putIfAbsent((UUID) arguments[0], BigDecimal.ZERO);
                    yield answer(arguments[1], account((UUID) arguments[0]));
                }
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private Currency currency() {
            return proxy(Currency.class, (method, arguments) -> switch (method) {
                case "getIdentifier" -> currencyId;
                case "getPrecision" -> 0;
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private PlayerAccount account(UUID who) {
            return proxy(PlayerAccount.class, (method, arguments) -> switch (method) {
                case "getUniqueId" -> who;
                case "retrieveBalance" -> answer(arguments[1], balances.getOrDefault(who, BigDecimal.ZERO));
                case "withdrawBalance" -> answer(arguments[3], move(who, ((BigDecimal) arguments[0]).negate()));
                case "depositBalance" -> answer(arguments[3], move(who, (BigDecimal) arguments[0]));
                default -> throw new UnsupportedOperationException(method);
            });
        }

        private BigDecimal move(UUID who, BigDecimal by) {
            BigDecimal after = balances.getOrDefault(who, BigDecimal.ZERO).add(by);
            balances.put(who, after);
            return after;
        }

        /** Treasury answers a subscriber. One that is told nothing is an economy that never answers. */
        @SuppressWarnings("unchecked")
        private @Nullable Object answer(Object subscriber, Object value) {
            if (answers) {
                ((EconomySubscriber<Object>) subscriber).succeed(value);
            }
            return null;
        }
    }

    /** What a fake does with one call: the name of the method, and what it was given. */
    private interface Answers {
        @Nullable Object to(String method, Object[] arguments);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> shape, Answers answers) {
        InvocationHandler handler = (self, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> shape.getSimpleName();
                    case "hashCode" -> System.identityHashCode(self);
                    default -> self == (arguments == null ? null : arguments[0]);
                };
            }
            return answers.to(method.getName(), arguments == null ? new Object[0] : arguments);
        };
        return (T) Proxy.newProxyInstance(shape.getClassLoader(), new Class<?>[] {shape}, handler);
    }
}

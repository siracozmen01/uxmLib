package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** One economy plugin, read through a description of its methods rather than compiled against. */
class BridgedWalletTest {

    private static final System.Logger LOG = System.getLogger(BridgedWalletTest.class.getName());
    private static final String CALLER = "uxmLibTest";

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
    @DisplayName("the Vault shape is read through its response object")
    void readsTheVaultShape() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.balance(ada, "")).isEqualTo(100);
        assertThat(wallet.withdraw(ada, "", 40)).isTrue();
        assertThat(vault.balance()).isEqualTo(60);
    }

    @Test
    @DisplayName("a take the economy refuses is read as refused, and nothing is taken")
    void readsARefusal() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(10);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.withdraw(ada, "", 40)).isFalse();
        assertThat(vault.balance()).isEqualTo(10);
    }

    @Test
    @DisplayName("an economy of one balance is never handed the name of a second, so an unknown name reads zero")
    void refusesACurrencyAnEconomyOfOneBalanceCannotHold() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        // Vault's two-argument getBalance takes a world. A wallet that passed "coins" into it would read
        // the balance of a world nobody has, so the name is refused before the call.
        assertThat(wallet.balance(ada, "coins")).isZero();
        assertThat(wallet.withdraw(ada, "coins", 1)).isFalse();
        assertThat(vault.balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("an economy of whole numbers is handed a whole number")
    void handsAWholeNumberToAWholeEconomy() {
        FakeEconomies.PointsShaped points = new FakeEconomies.PointsShaped(50);
        BridgedWallet wallet = wallet(Economies.playerPoints(), points);

        assertThat(wallet.balance(ada, "")).isEqualTo(50);
        assertThat(wallet.withdraw(ada, "", 20)).isTrue();
        assertThat(points.points()).isEqualTo(30);
    }

    @Test
    @DisplayName("an economy of whole numbers cannot be paid a fraction, and nothing is taken trying")
    void refusesAFractionToAWholeEconomy() {
        FakeEconomies.PointsShaped points = new FakeEconomies.PointsShaped(50);
        BridgedWallet wallet = wallet(Economies.playerPoints(), points);

        assertThat(wallet.withdraw(ada, "", 1.5)).isFalse();
        assertThat(points.points()).isEqualTo(50);
    }

    @Test
    @DisplayName("a plugin that is not there reads zero and takes nothing, and never fails")
    void staysQuietWhenThePluginIsAbsent() {
        BridgedWallet wallet = wallet(Economies.vault(), null);

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a plugin that renamed its methods turns its own wallet off")
    void staysOffWhenTheNamesChanged() {
        BridgedWallet wallet = wallet(Economies.vault(), new FakeEconomies.Renamed());

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a call that fails is read as not having happened")
    void readsAFailureAsNoPayment() {
        BridgedWallet wallet = wallet(Economies.vault(), new FakeEconomies.Broken());

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a utility class is called on no object, with a currency object and a decimal")
    void readsAStaticUtilityClass() {
        FakeEconomies.Held bits = FakeEconomies.Held.of("bits", "100.50");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.balance(ada, "bits")).isEqualTo(100.50);
        assertThat(bits.balance()).isEqualByComparingTo("100.50");
    }

    @Test
    @DisplayName("an economy with one adjust method is given a negative number for a take")
    void takesByGivingANegativeNumber() {
        FakeEconomies.Held bits = FakeEconomies.Held.of("negating", "40");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.withdraw(ada, "negating", 15)).isTrue();
        assertThat(bits.balance()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("an economy that cannot refuse is read first, so an overdraft takes nothing at all")
    void readsTheBalanceBeforeAnEconomyThatCannotRefuse() {
        FakeEconomies.Held bits = FakeEconomies.Held.of("short", "10");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.withdraw(ada, "short", 11)).isFalse();
        assertThat(bits.balance()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("a currency the plugin does not hold reads zero and takes nothing")
    void staysOffWhenThePluginHasNoSuchCurrency() {
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.balance(ada, "nothing of that name")).isZero();
        assertThat(wallet.withdraw(ada, "nothing of that name", 1)).isFalse();
    }

    @Test
    @DisplayName("an economy that names its currencies is not asked about the empty name")
    void refusesTheEmptyNameOnAnEconomyOfSeveralBalances() {
        FakeEconomies.Held.of("bits", "50");
        BridgedWallet wallet = wallet(ecoBitsShaped(), FakeEconomies.UtilityShaped.class);

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("VaultUnlocked is told who is asking, and keeps the last penny of a decimal")
    void readsTheUnlockedShape() {
        FakeEconomies.UnlockedShaped unlocked = new FakeEconomies.UnlockedShaped("100.05");
        BridgedWallet wallet = wallet(Economies.vaultUnlocked(CALLER), unlocked);

        assertThat(wallet.balance(ada, "")).isEqualTo(100.05);
        assertThat(unlocked.lastCaller()).isEqualTo(CALLER);
        assertThat(wallet.withdraw(ada, "", 0.05)).isTrue();
        assertThat(unlocked.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("a take VaultUnlocked refuses is read as refused")
    void readsAnUnlockedRefusal() {
        FakeEconomies.UnlockedShaped unlocked = new FakeEconomies.UnlockedShaped("1");
        BridgedWallet wallet = wallet(Economies.vaultUnlocked(CALLER), unlocked);

        assertThat(wallet.withdraw(ada, "", 40)).isFalse();
        assertThat(unlocked.balance()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("nobody is not a player, so nothing is read and nothing is taken")
    void answersForNoPlayerAtAll() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.balance(null, "")).isZero();
        assertThat(wallet.withdraw(null, "", 1)).isFalse();
        assertThat(vault.balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("a take of nothing takes nothing and succeeds, as the wallet contract says")
    void takesNothingForANonPositiveAmount() {
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet = wallet(Economies.vault(), vault);

        assertThat(wallet.withdraw(ada, "", 0)).isTrue();
        assertThat(wallet.withdraw(ada, "", -5)).isTrue();
        assertThat(vault.balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("the object behind the economy is asked for once and kept")
    void resolvesTheHandleOnceAndKeepsIt() {
        AtomicInteger asked = new AtomicInteger();
        FakeEconomies.VaultShaped vault = new FakeEconomies.VaultShaped(100);
        BridgedWallet wallet =
                new BridgedWallet(Economies.vault(), counting(vault, asked), PlayerArguments.ofPlayer(), LOG);

        wallet.balance(ada, "");
        wallet.balance(ada, "");
        wallet.withdraw(ada, "", 1);

        assertThat(asked).hasValue(1);
    }

    @Test
    @DisplayName("a wallet says which economy it reads, so a caller can log it")
    void namesItsOwnBinding() {
        assertThat(wallet(Economies.vault(), null).binding().pluginName()).isEqualTo("Vault");
    }

    /** The EcoBits description, pointed at the classes this test owns. */
    private static EconomyBinding ecoBitsShaped() {
        return new EconomyBinding(
                "EcoBits",
                FakeEconomies.UtilityShaped.class.getName(),
                EconomyBinding.Access.CLASS,
                null,
                "getBalance",
                "adjustBalance",
                EconomyBinding.Argument.PLAYER_ID,
                EconomyBinding.Answer.NOTHING,
                EconomyBinding.Pools.byObject(FakeEconomies.Held.class.getName(), "getByID"),
                new EconomyBinding.Calls(true, null));
    }

    private static BridgedWallet wallet(EconomyBinding binding, @Nullable Object provider) {
        return new BridgedWallet(binding, holding(provider), PlayerArguments.ofPlayer(), LOG);
    }

    /** The seam a wallet reads its object from, holding one object or nothing at all. */
    private static EconomyProviders holding(@Nullable Object provider) {
        return counting(provider, new AtomicInteger());
    }

    /** The same seam, counting how often it was asked. */
    private static EconomyProviders counting(@Nullable Object provider, AtomicInteger asked) {
        return new EconomyProviders() {

            @Override
            public Optional<Object> provider(EconomyBinding binding) {
                asked.incrementAndGet();
                return Optional.ofNullable(provider);
            }

            @Override
            public Optional<Object> service(String pluginName, String className) {
                return Optional.empty();
            }
        };
    }
}

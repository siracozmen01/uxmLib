package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** A balance behind a placeholder, taken by a console line. */
class PlaceholderWalletTest {

    private static final PlaceholderWallet.Pool TOKENS =
            PlaceholderWallet.Pool.of("%tokens_balance%", "tm remove {player} {amount}");

    private final List<String> sent = new ArrayList<>();

    private ServerMock server;
    private Player ada;
    private String answer = "100";
    private boolean serverTakesTheLine = true;

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
    @DisplayName("the balance is the number the placeholder answered with")
    void readsTheBalance() {
        assertThat(wallet().balance(ada, "")).isEqualTo(100);
    }

    @Test
    @DisplayName("a separator between the groups of three is removed before the number is read")
    void readsAnumberWithSeparators() {
        answer = "1,250.50";
        PlaceholderWallet wallet = new PlaceholderWallet(
                Map.of("", new PlaceholderWallet.Pool("%tokens_balance%", "tm remove {player} {amount}", ",")),
                this::read,
                this::run);

        assertThat(wallet.balance(ada, "")).isEqualTo(1250.50);
    }

    @Test
    @DisplayName("an answer that is not a number is read as zero, so nothing is bought with it")
    void readsWhatIsNotAnumberAsZero() {
        answer = "no such placeholder";

        assertThat(wallet().balance(ada, "")).isZero();
        assertThat(wallet().withdraw(ada, "", 1)).isFalse();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("a take sends the line, with the name and the amount written into it")
    void sendsTheTakeLine() {
        assertThat(wallet().withdraw(ada, "", 40)).isTrue();
        assertThat(sent).containsExactly("tm remove Ada 40");
    }

    @Test
    @DisplayName("a take of more than the player has sends nothing at all")
    void refusesToTakeWhatIsNotThere() {
        assertThat(wallet().withdraw(ada, "", 140)).isFalse();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("a line the server would not take is a take that did not happen")
    void readsArefusedLineAsNoPayment() {
        serverTakesTheLine = false;

        assertThat(wallet().withdraw(ada, "", 40)).isFalse();
    }

    @Test
    @DisplayName("the id of the player goes into a line that asks for it")
    void writesTheIdIntoTheLine() {
        PlaceholderWallet wallet = new PlaceholderWallet(
                Map.of("", PlaceholderWallet.Pool.of("%tokens_balance%", "points take {uuid} {amount}")),
                this::read,
                this::run);

        assertThat(wallet.withdraw(ada, "", 10)).isTrue();
        assertThat(sent).containsExactly("points take " + ada.getUniqueId() + " 10");
    }

    @Test
    @DisplayName("a currency this wallet was not given reads zero and takes nothing")
    void answersNothingForAcurrencyItDoesNotHold() {
        assertThat(wallet().balance(ada, "gems")).isZero();
        assertThat(wallet().withdraw(ada, "gems", 1)).isFalse();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("one wallet holds as many pools as it was given, each under its own name")
    void holdsMoreThanOnePool() {
        PlaceholderWallet wallet = new PlaceholderWallet(
                Map.of(
                        "tokens", PlaceholderWallet.Pool.of("%tokens_balance%", "tm remove {player} {amount}"),
                        "gems", PlaceholderWallet.Pool.of("%gems_balance%", "gems take {player} {amount}")),
                (player, placeholder) -> Optional.of("%gems_balance%".equals(placeholder) ? "7" : "100"),
                this::run);

        assertThat(wallet.balance(ada, "tokens")).isEqualTo(100);
        assertThat(wallet.balance(ada, "gems")).isEqualTo(7);
        assertThat(wallet.withdraw(ada, "gems", 5)).isTrue();
        assertThat(sent).containsExactly("gems take Ada 5");
    }

    @Test
    @DisplayName("nobody is not a player, and a take of nothing succeeds without sending a line")
    void answersForNoPlayerAndForNoAmount() {
        assertThat(wallet().balance(null, "")).isZero();
        assertThat(wallet().withdraw(null, "", 1)).isFalse();
        assertThat(wallet().withdraw(ada, "", 0)).isTrue();
        assertThat(sent).isEmpty();
    }

    @Test
    @DisplayName("a pool that names no placeholder or no command is not a pool")
    void refusesApoolThatNamesNothing() {
        assertThatThrownBy(() -> PlaceholderWallet.Pool.of(" ", "tm remove {player} {amount}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaceholderWallet.Pool.of("%tokens_balance%", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a fractional amount is written in plain digits, with no grouping of its own")
    void writesTheAmountInPlainDigits() {
        answer = "100";

        assertThat(wallet().withdraw(ada, "", 12.50)).isTrue();
        assertThat(sent).containsExactly("tm remove Ada 12.5");
    }

    private PlaceholderWallet wallet() {
        return new PlaceholderWallet(Map.of("", TOKENS), this::read, this::run);
    }

    private Optional<String> read(Player player, String placeholder) {
        return Optional.of(answer);
    }

    private boolean run(String line) {
        if (!serverTakesTheLine) {
            return false;
        }
        sent.add(line);
        return true;
    }
}

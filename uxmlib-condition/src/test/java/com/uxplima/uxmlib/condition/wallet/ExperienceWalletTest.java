package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The player's own experience as money.
 *
 * <p>Every test runs on the thread the mock server hands back, which is the thread that owns the player.
 * That is the lane this wallet demands, and running the tests anywhere else would prove nothing.
 */
class ExperienceWalletTest {

    private static final ExperienceWallet POINTS = ExperienceWallet.ofPoints();
    private static final ExperienceWallet LEVELS = ExperienceWallet.ofLevels();

    private ServerMock server;
    private PlayerMock ada;

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
    @DisplayName("a balance in points is the whole total, the half level included")
    void readsTheBalanceInPoints() {
        standing(10, 0.5f);

        assertThat(POINTS.balance(ada, "")).isEqualTo(ExperiencePoints.totalOf(10, 0.5f));
    }

    @Test
    @DisplayName("a balance in levels is the number on the player's own screen")
    void readsTheBalanceInLevels() {
        standing(10, 0.5f);

        assertThat(LEVELS.balance(ada, "")).isEqualTo(10);
    }

    @Test
    @DisplayName("thirty levels and thirty points are not the same money")
    void countsPointsAndLevelsAsTwoCurrencies() {
        standing(30, 0f);

        assertThat(POINTS.balance(ada, "")).isEqualTo(1395);
        assertThat(LEVELS.balance(ada, "")).isEqualTo(30);
    }

    @Test
    @DisplayName("a take of thirty leaves a different player behind in each unit")
    void spendsThirtyPointsAndThirtyLevelsDifferently() {
        standing(30, 0f);
        assertThat(POINTS.withdraw(ada, "", 30)).isTrue();
        int afterThirtyPoints = ada.getLevel();

        standing(30, 0f);
        assertThat(LEVELS.withdraw(ada, "", 30)).isTrue();
        int afterThirtyLevels = ada.getLevel();

        assertThat(afterThirtyPoints).isEqualTo(29);
        assertThat(afterThirtyLevels).isZero();
    }

    @Test
    @DisplayName("a take in points writes the level and the fraction back from what is left")
    void spendsPointsAndLeavesTheRest() {
        standing(10, 0.5f);
        int held = ExperiencePoints.totalOf(10, 0.5f);

        assertThat(POINTS.withdraw(ada, "", 14)).isTrue();

        assertThat(POINTS.balance(ada, "")).isEqualTo(held - 14);
        assertThat(ada.getLevel()).isEqualTo(10);
        assertThat(ada.getExp()).isEqualTo(0f);
    }

    @Test
    @DisplayName("a take in levels lowers the level and leaves the fraction where it stood")
    void spendsLevelsAndLeavesTheFraction() {
        standing(30, 0.5f);

        assertThat(LEVELS.withdraw(ada, "", 1)).isTrue();

        assertThat(ada.getLevel()).isEqualTo(29);
        assertThat(ada.getExp()).isEqualTo(0.5f);
    }

    @ParameterizedTest
    @CsvSource({"16,352,15", "17,394,16", "31,1507,30", "32,1628,31", "1,7,0"})
    @DisplayName("a point spent at a bend in the curve drops exactly one level")
    void spendsApointAcrossEveryBend(int level, int total, int afterwards) {
        standing(level, 0f);

        assertThat(POINTS.balance(ada, "")).isEqualTo(total);
        assertThat(POINTS.withdraw(ada, "", 1)).isTrue();
        assertThat(ada.getLevel()).isEqualTo(afterwards);
        assertThat(POINTS.balance(ada, "")).isEqualTo(total - 1);
    }

    @Test
    @DisplayName("the whole balance can be spent, and it leaves the player at nothing")
    void spendsEverythingItHas() {
        standing(5, 0f);

        assertThat(POINTS.withdraw(ada, "", 55)).isTrue();

        assertThat(ada.getLevel()).isZero();
        assertThat(ada.getExp()).isEqualTo(0f);
        assertThat(POINTS.balance(ada, "")).isZero();
    }

    @Test
    @DisplayName("a take of more points than the player has spends none of them")
    void refusesToOverdrawPoints() {
        standing(5, 0f);

        assertThat(POINTS.withdraw(ada, "", 56)).isFalse();

        assertThat(ada.getLevel()).isEqualTo(5);
        assertThat(ada.getExp()).isEqualTo(0f);
        assertThat(POINTS.balance(ada, "")).isEqualTo(55);
    }

    @Test
    @DisplayName("a take of more levels than the player has spends none of them")
    void refusesToOverdrawLevels() {
        standing(5, 0.25f);

        assertThat(LEVELS.withdraw(ada, "", 6)).isFalse();

        assertThat(ada.getLevel()).isEqualTo(5);
        assertThat(ada.getExp()).isEqualTo(0.25f);
    }

    @Test
    @DisplayName("nothing this wallet does can leave a player below zero")
    void neverLeavesAplayerBelowZero() {
        standing(0, 0f);

        assertThat(POINTS.withdraw(ada, "", 1)).isFalse();
        assertThat(LEVELS.withdraw(ada, "", 1)).isFalse();

        assertThat(POINTS.balance(ada, "")).isZero();
        assertThat(LEVELS.balance(ada, "")).isZero();
        assertThat(ada.getLevel()).isZero();
    }

    @Test
    @DisplayName("an amount with a fraction in it cannot be paid in experience, and nothing moves")
    void refusesAnAmountThatIsNotWhole() {
        standing(10, 0f);

        assertThat(POINTS.withdraw(ada, "", 12.5)).isFalse();
        assertThat(LEVELS.withdraw(ada, "", 1.5)).isFalse();

        assertThat(ada.getLevel()).isEqualTo(10);
        assertThat(ada.getExp()).isEqualTo(0f);
    }

    @Test
    @DisplayName("an amount no int can count, and one that is no number at all, are both refused")
    void refusesAnAmountItCannotCount() {
        standing(10, 0f);

        assertThat(POINTS.withdraw(ada, "", 3_000_000_000d)).isFalse();
        assertThat(POINTS.withdraw(ada, "", 1e30)).isFalse();
        assertThat(POINTS.withdraw(ada, "", Double.POSITIVE_INFINITY)).isFalse();
        assertThat(POINTS.withdraw(ada, "", Double.NaN)).isFalse();

        assertThat(ada.getLevel()).isEqualTo(10);
    }

    @Test
    @DisplayName("a currency this wallet was not given reads zero and takes nothing")
    void answersNothingForAcurrencyItDoesNotHold() {
        standing(10, 0f);

        assertThat(POINTS.balance(ada, "levels")).isZero();
        assertThat(POINTS.withdraw(ada, "levels", 1)).isFalse();
        assertThat(ada.getLevel()).isEqualTo(10);
    }

    @Test
    @DisplayName("one wallet holds both units at once, each under its own name")
    void holdsBothUnitsUnderTheirOwnNames() {
        ExperienceWallet wallet = new ExperienceWallet(
                Map.of("xp", ExperienceWallet.Unit.POINTS, "levels", ExperienceWallet.Unit.LEVELS));
        standing(30, 0f);

        assertThat(wallet.balance(ada, "xp")).isEqualTo(1395);
        assertThat(wallet.balance(ada, "levels")).isEqualTo(30);

        assertThat(wallet.withdraw(ada, "levels", 5)).isTrue();
        assertThat(ada.getLevel()).isEqualTo(25);
        assertThat(wallet.balance(ada, "xp")).isEqualTo(ExperiencePoints.totalAt(25));
    }

    @Test
    @DisplayName("a player who is not online has no experience anyone can reach")
    void answersNothingForAplayerWhoLeft() {
        standing(10, 0f);
        ada.disconnect();

        assertThat(POINTS.balance(ada, "")).isZero();
        assertThat(LEVELS.balance(ada, "")).isZero();
        assertThat(POINTS.withdraw(ada, "", 1)).isFalse();
        assertThat(LEVELS.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("nobody is not a player, and a take of nothing succeeds without touching anyone")
    void answersForNoPlayerAndForNoAmount() {
        standing(10, 0f);

        assertThat(POINTS.balance(null, "")).isZero();
        assertThat(POINTS.withdraw(null, "", 1)).isFalse();
        assertThat(POINTS.withdraw(ada, "", 0)).isTrue();
        assertThat(POINTS.withdraw(ada, "", -5)).isTrue();

        assertThat(ada.getLevel()).isEqualTo(10);
    }

    @Test
    @DisplayName("the balance a take leaves behind is the balance the take was measured against")
    void leavesExactlyWhatItSaidItWould() {
        standing(40, 0.75f);
        double before = POINTS.balance(ada, "");

        assertThat(POINTS.withdraw(ada, "", 500)).isTrue();

        assertThat(POINTS.balance(ada, "")).isCloseTo(before - 500, within(1d));
    }

    /** Put the player at {@code level} with {@code progress} of the way into the next one. */
    private void standing(int level, float progress) {
        ada.setLevel(level);
        ada.setExp(progress);
    }
}

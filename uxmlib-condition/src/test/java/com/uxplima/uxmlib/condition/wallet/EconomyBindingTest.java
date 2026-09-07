package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Access;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Answer;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Argument;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Calls;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Pools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A description of an economy refuses to be written in a way the reader could not follow. */
class EconomyBindingTest {

    @Test
    @DisplayName("a static access with no accessor names nothing the reader could call")
    void refusesAstaticAccessWithNoAccessor() {
        assertThatThrownBy(() -> new EconomyBinding(
                        "Money",
                        "com.example.Money",
                        Access.STATIC,
                        null,
                        "getBalance",
                        "take",
                        Argument.PLAYER_ID,
                        Answer.BOOLEAN,
                        Pools.one(),
                        Calls.simple()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("static access");
    }

    @Test
    @DisplayName("a currency that is an object needs the class that hands it out")
    void refusesAcurrencyObjectWithNoHolder() {
        assertThatThrownBy(() -> new Pools(Pools.Style.BY_OBJECT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pools(Pools.Style.BY_OBJECT, "com.example.Currencies", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the three shapes of currency are what they say they are")
    void namesTheThreeShapesOfCurrency() {
        assertThat(Pools.one().style()).isEqualTo(Pools.Style.ONE);
        assertThat(Pools.byName().style()).isEqualTo(Pools.Style.BY_NAME);
        assertThat(Pools.byObject("com.example.Currencies", "getByID").lookupMethod())
                .isEqualTo("getByID");
    }

    @Test
    @DisplayName("a plugin that asks who is moving the money is told, and one that does not is not")
    void introducesTheCallerOnlyWhereItIsAsked() {
        assertThat(Calls.simple().introduction()).isEmpty();
        assertThat(new Calls(false, "uxmLib").introduction()).contains("uxmLib");
    }

    @Test
    @DisplayName("VaultUnlocked records who moved the money, so it cannot be left unnamed")
    void refusesAnUnnamedCallerForVaultUnlocked() {
        assertThatThrownBy(() -> Economies.vaultUnlocked(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the four shipped descriptions say which plugin and which methods they read")
    void describesTheFourShippedEconomies() {
        assertThat(Economies.vault().pluginName()).isEqualTo("Vault");
        assertThat(Economies.vault().answer()).isEqualTo(Answer.VAULT_RESPONSE);
        assertThat(Economies.vaultUnlocked("uxmLib").takeMethod()).isEqualTo("withdraw");
        assertThat(Economies.playerPoints().accessorName()).isEqualTo("getInstance.getAPI");
        assertThat(Economies.playerPoints().answer()).isEqualTo(Answer.BOOLEAN);
        // EcoBits cannot refuse an overdraft, which is what makes the wallet read the balance first.
        assertThat(Economies.ecoBits().answer()).isEqualTo(Answer.NOTHING);
        assertThat(Economies.ecoBits().pools().style()).isEqualTo(Pools.Style.BY_OBJECT);
        assertThat(Economies.ecoBits().calls().takeNegates()).isTrue();
    }
}

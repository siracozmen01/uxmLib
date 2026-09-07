package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The door onto PlaceholderAPI, on a server that does not have it.
 *
 * <p>That is the case this library has to be right about. The plugin manager is asked first, so the class
 * that names a PlaceholderAPI type is never loaded, and a read answers with nothing instead of failing.
 */
class ServerPlaceholdersTest {

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
    @DisplayName("a server without PlaceholderAPI answers nothing, and never fails")
    void readsNothingWithoutThePlugin() {
        assertThat(new ServerPlaceholders().read(ada, "%tokens_balance%")).isEmpty();
    }

    @Test
    @DisplayName("a whole wallet over that absent plugin reads zero and takes nothing")
    void readsZeroWithoutThePlugin() {
        PlaceholderWallet wallet = PlaceholderWallet.ofServer(
                PlaceholderWallet.Pool.of("%tokens_balance%", "tm remove {player} {amount}"));

        assertThat(wallet.balance(ada, "")).isZero();
        assertThat(wallet.withdraw(ada, "", 1)).isFalse();
    }

    @Test
    @DisplayName("a wallet of several pools can be built from the server too")
    void buildsAwalletOfSeveralPoolsFromTheServer() {
        PlaceholderWallet wallet = PlaceholderWallet.ofServer(
                Map.of("tokens", PlaceholderWallet.Pool.of("%tokens_balance%", "tm remove {player} {amount}")));

        assertThat(wallet.balance(ada, "tokens")).isZero();
    }

    @Test
    @DisplayName("the plugin this door is guarded by is the one PlaceholderAPI is called")
    void namesThePluginItGuardsOn() {
        assertThat(ServerPlaceholders.PLUGIN).isEqualTo("PlaceholderAPI");
    }
}

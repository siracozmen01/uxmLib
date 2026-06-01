package com.uxplima.uxmlib.hook.economy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Verifies the economy abstraction's null-object behaviour when no economy is installed. */
class EconomyBridgeTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void findIsEmptyWithoutAnEconomy() {
        assertThat(EconomyBridge.find()).isEmpty();
    }

    @Test
    void orDummyNeverNullChecksAndDeclinesEverything() {
        EconomyBridge economy = EconomyBridge.orDummy();
        var player = MockBukkit.getMock().addPlayer();

        assertThat(economy.isPresent()).isFalse();
        assertThat(economy.balance(player)).isZero();
        assertThat(economy.has(player, 100)).isFalse();
        assertThat(economy.withdraw(player, 100)).isFalse();
        assertThat(economy.deposit(player, 100)).isFalse();
    }
}

package com.uxplima.uxmlib.condition;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The inventory-backed store counts and consumes real Bukkit materials against a mock server. The test that
 * carries the weight is the split-stack refusal: a player holding two part-stacks that together fall one
 * short must end the call holding both of them untouched.
 */
class InventoryItemStoreTest {

    private ServerMock server;
    private final ItemStore store = ItemStore.inventory();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void countsEveryMatchingStack() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 7));
        player.getInventory().addItem(new ItemStack(Material.EMERALD, 3));

        assertThat(store.count(player, "diamond")).isEqualTo(12);
        assertThat(store.count(player, "emerald")).isEqualTo(3);
        assertThat(store.count(player, "gold_ingot")).isZero();
    }

    @Test
    void anUnknownMaterialCountsZeroAndCannotBeTaken() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        assertThat(store.count(player, "not_a_material")).isZero();
        assertThat(store.take(player, "not_a_material", 1)).isFalse();
    }

    @Test
    void takesAcrossSeveralStacks() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 5));

        assertThat(store.take(player, "diamond", 8)).isTrue();
        assertThat(store.count(player, "diamond")).isEqualTo(2);
    }

    @Test
    void anEmptiedStackLeavesTheSlotFree() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 4));

        assertThat(store.take(player, "diamond", 4)).isTrue();
        assertThat(store.count(player, "diamond")).isZero();
        assertThat(player.getInventory().contains(Material.DIAMOND)).isFalse();
    }

    @Test
    void aTakeThatFallsOneShortConsumesNothing() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));

        assertThat(store.take(player, "diamond", 7)).isFalse();
        // The whole cost is measured first, so neither part-stack was touched on the way to the refusal.
        assertThat(store.count(player, "diamond")).isEqualTo(6);
    }

    @Test
    void aNonPositiveAmountTakesNothingAndSucceeds() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));

        assertThat(store.take(player, "diamond", 0)).isTrue();
        assertThat(store.count(player, "diamond")).isEqualTo(3);
    }

    @Test
    void withoutAPlayerItCountsZeroAndRefuses() {
        assertThat(store.count(null, "diamond")).isZero();
        assertThat(store.take(null, "diamond", 1)).isFalse();
    }

    @Test
    void aMaterialNameIsReadCaseInsensitivelyAndTrimmed() {
        PlayerMock player = server.addPlayer();
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, 2));

        assertThat(store.count(player, "  DIAMOND ")).isEqualTo(2);
    }
}

package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class ItemsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void editPdcWritesThroughToTheItem() {
        NamespacedKey key = NamespacedKey.fromString("uxmlib:coins");
        ItemStack item = new ItemStack(Material.STONE);

        Items.editPdc(item, pdc -> pdc.set(key, PersistentDataType.INTEGER, 7));

        Integer stored = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        assertThat(stored).isEqualTo(7);
    }

    @Test
    void giveAddsItemsToTheInventory() {
        PlayerMock player = server.addPlayer();

        Items.give(player, new ItemStack(Material.DIAMOND, 5));

        assertThat(player.getInventory().contains(Material.DIAMOND, 5)).isTrue();
    }
}

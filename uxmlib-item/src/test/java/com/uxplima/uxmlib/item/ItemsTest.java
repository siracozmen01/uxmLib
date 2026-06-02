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

    @Test
    void isSimilarComparesOnlyTheOneNamedPdcKey() {
        NamespacedKey id = NamespacedKey.fromString("uxmlib:id");
        NamespacedKey other = NamespacedKey.fromString("uxmlib:other");

        ItemStack a = ItemBuilder.of(Material.STONE)
                .name(net.kyori.adventure.text.Component.text("A"))
                .editPersistentData(pdc -> {
                    pdc.set(id, PersistentDataType.STRING, "x");
                    pdc.set(other, PersistentDataType.STRING, "left");
                })
                .build();
        ItemStack b = ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(net.kyori.adventure.text.Component.text("B"))
                .editPersistentData(pdc -> {
                    pdc.set(id, PersistentDataType.STRING, "x");
                    pdc.set(other, PersistentDataType.STRING, "right");
                })
                .build();

        // Same value under id, despite different material, name and other keys.
        assertThat(Items.isSimilar(a, b, id, PersistentDataType.STRING)).isTrue();
    }

    @Test
    void isSimilarRejectsAMismatchedOrAbsentKey() {
        NamespacedKey id = NamespacedKey.fromString("uxmlib:id");

        ItemStack a = ItemBuilder.of(Material.STONE)
                .editPersistentData(pdc -> pdc.set(id, PersistentDataType.STRING, "x"))
                .build();
        ItemStack different = ItemBuilder.of(Material.STONE)
                .editPersistentData(pdc -> pdc.set(id, PersistentDataType.STRING, "y"))
                .build();
        ItemStack absent = ItemBuilder.of(Material.STONE).build();

        assertThat(Items.isSimilar(a, different, id, PersistentDataType.STRING)).isFalse();
        assertThat(Items.isSimilar(a, absent, id, PersistentDataType.STRING)).isFalse();
        // Both absent: equal (both empty).
        assertThat(Items.isSimilar(absent, ItemBuilder.of(Material.STONE).build(), id, PersistentDataType.STRING))
                .isTrue();
    }

    @Test
    void schedulerAwareGiveHopsToTheEntityRegionBeforeMutating() {
        PlayerMock player = server.addPlayer();
        java.util.concurrent.atomic.AtomicReference<org.bukkit.entity.Entity> hoppedTo =
                new java.util.concurrent.atomic.AtomicReference<>();
        com.uxplima.uxmlib.scheduler.Scheduler scheduler = new ItemInlineScheduler() {
            @Override
            public com.uxplima.uxmlib.scheduler.TaskHandle entity(org.bukkit.entity.Entity entity, Runnable task) {
                hoppedTo.set(entity);
                task.run();
                return super.async(() -> {});
            }
        };

        Items.give(scheduler, player, new ItemStack(Material.DIAMOND, 5));

        assertThat(hoppedTo.get()).isSameAs(player);
        assertThat(player.getInventory().contains(Material.DIAMOND, 5)).isTrue();
    }
}

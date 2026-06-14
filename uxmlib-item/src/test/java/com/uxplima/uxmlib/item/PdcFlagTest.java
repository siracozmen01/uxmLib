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

class PdcFlagTest {

    private static final NamespacedKey SOULBOUND = NamespacedKey.minecraft("soulbound");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setTrueReadsTrue() {
        ItemStack item = new ItemStack(Material.STONE);

        Items.editPdc(item, pdc -> PdcFlag.set(pdc, SOULBOUND, true));

        assertThat(PdcFlag.get(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isTrue();
    }

    @Test
    void setFalseReadsFalse() {
        ItemStack item = new ItemStack(Material.STONE);

        Items.editPdc(item, pdc -> PdcFlag.set(pdc, SOULBOUND, false));

        assertThat(PdcFlag.get(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isFalse();
    }

    @Test
    void getReadsAbsentKeyAsFalse() {
        ItemStack item = new ItemStack(Material.STONE);

        assertThat(PdcFlag.get(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isFalse();
    }

    @Test
    void getOrDefaultReturnsFallbackWhenAbsent() {
        ItemStack item = new ItemStack(Material.STONE);

        assertThat(PdcFlag.getOrDefault(item.getItemMeta().getPersistentDataContainer(), SOULBOUND, true))
                .isTrue();
    }

    @Test
    void getOrDefaultReturnsFalseForAPresentZero() {
        ItemStack item = new ItemStack(Material.STONE);

        Items.editPdc(item, pdc -> PdcFlag.set(pdc, SOULBOUND, false));

        assertThat(PdcFlag.getOrDefault(item.getItemMeta().getPersistentDataContainer(), SOULBOUND, true))
                .isFalse();
    }

    @Test
    void hasReflectsWhetherAFlagIsPresent() {
        ItemStack item = new ItemStack(Material.STONE);
        assertThat(PdcFlag.has(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isFalse();

        Items.editPdc(item, pdc -> PdcFlag.set(pdc, SOULBOUND, false));

        assertThat(PdcFlag.has(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isTrue();
    }

    @Test
    void removeClearsTheFlag() {
        ItemStack item = new ItemStack(Material.STONE);
        Items.editPdc(item, pdc -> PdcFlag.set(pdc, SOULBOUND, true));

        Items.editPdc(item, pdc -> PdcFlag.remove(pdc, SOULBOUND));

        assertThat(PdcFlag.has(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isFalse();
        assertThat(PdcFlag.get(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isFalse();
    }

    @Test
    void roundTripsThroughLiveItemMeta() {
        ItemStack item = new ItemStack(Material.STONE);

        Items.editPdc(item, pdc -> PdcFlag.set(pdc, SOULBOUND, true));
        assertThat(PdcFlag.get(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isTrue();

        Items.editPdc(item, pdc -> PdcFlag.set(pdc, SOULBOUND, false));
        assertThat(PdcFlag.get(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isFalse();
    }

    @Test
    void readsAValueWrittenByTheRawByteIdiom() {
        ItemStack item = new ItemStack(Material.STONE);

        Items.editPdc(item, pdc -> pdc.set(SOULBOUND, PersistentDataType.BYTE, (byte) 1));

        assertThat(PdcFlag.get(item.getItemMeta().getPersistentDataContainer(), SOULBOUND))
                .isTrue();
    }
}

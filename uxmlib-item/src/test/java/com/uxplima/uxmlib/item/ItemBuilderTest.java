package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ItemBuilderTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setsNameAndLoreAsComponents() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
                .name(Text.mini("<red>Blade"))
                .lore(Text.mini("<gray>line one"), Text.mini("<gray>line two"))
                .build();

        assertThat(Text.plain(item.getItemMeta().displayName())).isEqualTo("Blade");
        assertThat(item.getItemMeta().lore()).hasSize(2);
        assertThat(Text.plain(item.getItemMeta().lore().get(0))).isEqualTo("line one");
    }

    @Test
    void appendsLore() {
        ItemStack item = ItemBuilder.of(Material.PAPER)
                .lore(Component.text("first"))
                .addLore(Component.text("second"))
                .build();

        assertThat(item.getItemMeta().lore()).hasSize(2);
        assertThat(Text.plain(item.getItemMeta().lore().get(1))).isEqualTo("second");
    }

    @Test
    void setsAmountAndRejectsOutOfRange() {
        ItemStack item = ItemBuilder.of(Material.STONE).amount(16).build();
        assertThat(item.getAmount()).isEqualTo(16);

        assertThatThrownBy(() -> ItemBuilder.of(Material.STONE).amount(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesFlagsAndUnbreakable() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_PICKAXE)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
                .unbreakable(true)
                .build();

        assertThat(item.getItemMeta().getItemFlags()).contains(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        assertThat(item.getItemMeta().isUnbreakable()).isTrue();
    }

    @Test
    void appliesDamageToADamageableItem() {
        ItemStack item = ItemBuilder.of(Material.IRON_AXE).damage(50).build();

        assertThat(item.getItemMeta()).isInstanceOf(Damageable.class);
        assertThat(((Damageable) item.getItemMeta()).getDamage()).isEqualTo(50);
    }

    @Test
    void rejectsAirAndNegativeDamage() {
        assertThatThrownBy(() -> ItemBuilder.of(Material.AIR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.IRON_AXE).damage(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSkullOnANonHead() {
        assertThatThrownBy(() -> ItemBuilder.of(Material.STONE).skull(SkullData.ofName("Notch")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesPersistentData() {
        NamespacedKey key = NamespacedKey.fromString("uxmlib:test");
        ItemStack item = ItemBuilder.of(Material.STONE)
                .editPersistentData(pdc -> pdc.set(key, PersistentDataType.STRING, "value"))
                .build();

        String stored = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        assertThat(stored).isEqualTo("value");
    }

    @Test
    void buildReturnsIndependentCopies() {
        ItemBuilder builder = ItemBuilder.of(Material.STONE).amount(1);
        ItemStack first = builder.build();
        builder.amount(32);
        ItemStack second = builder.build();

        assertThat(first.getAmount()).isEqualTo(1);
        assertThat(second.getAmount()).isEqualTo(32);
    }
}

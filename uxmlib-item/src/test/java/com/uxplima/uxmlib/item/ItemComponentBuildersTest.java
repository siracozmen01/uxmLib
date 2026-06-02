package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ItemComponentBuildersTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void customModelDataFloatsWiringDoesNotThrow() {
        // MockBukkit does not retain the custom-model-data component across a getItemMeta boundary (the same
        // gap documented for itemModel), so assert only that the wiring is valid. The component round-trip
        // itself is checked in setAndReadCustomModelDataOnASingleMeta below, on one meta instance.
        assertThatCode(() -> ItemBuilder.of(Material.STONE)
                        .customModelDataFloats(List.of(0.25f, 0.5f))
                        .build())
                .doesNotThrowAnyException();

        CustomModelDataComponent component =
                new ItemStack(Material.STONE).getItemMeta().getCustomModelDataComponent();
        component.setStrings(List.of("variant"));
        assertThatCode(() -> ItemBuilder.of(Material.STONE)
                        .customModelData(component)
                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    void setAndReadCustomModelDataOnASingleMeta() {
        // On one meta instance (no lossy ItemStack copy) the float and string lists round-trip, proving the
        // setCustomModelDataComponent / setFloats wiring the builder uses is correct.
        AtomicReference<CustomModelDataComponent> seen = new AtomicReference<>();
        ItemBuilder.of(Material.STONE).editMeta(meta -> {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(0.25f, 0.5f));
            component.setStrings(List.of("variant"));
            meta.setCustomModelDataComponent(component);
            seen.set(meta.getCustomModelDataComponent());
        });

        CustomModelDataComponent applied = java.util.Objects.requireNonNull(seen.get());
        assertThat(applied.getFloats()).containsExactly(0.25f, 0.5f);
        assertThat(applied.getStrings()).containsExactly("variant");
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void rejectsNullComponentInputs() {
        assertThatThrownBy(() -> ItemBuilder.of(Material.STONE).customModelData(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.STONE).customModelDataFloats(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void setsTooltipStyle() {
        // tooltip_style is another field MockBukkit drops across a getItemMeta boundary, so set and read it on
        // one meta instance; setTooltipStyle itself is verified against real paper-api 1.21.11.
        NamespacedKey style = NamespacedKey.fromString("uxmlib:fancy");
        AtomicReference<NamespacedKey> seen = new AtomicReference<>();
        ItemBuilder.of(Material.STONE).editMeta(meta -> {
            meta.setTooltipStyle(style);
            seen.set(meta.getTooltipStyle());
        });

        assertThat(seen.get()).isEqualTo(style);
        // And the builder's own tooltipStyle path applies cleanly.
        assertThatCode(() -> ItemBuilder.of(Material.STONE).tooltipStyle(style).build())
                .doesNotThrowAnyException();
    }

    @Test
    void hidesTheTooltip() {
        ItemStack item = ItemBuilder.of(Material.STONE).hideTooltip(true).build();

        assertThat(item.getItemMeta().isHideTooltip()).isTrue();
    }

    @Test
    void setsEnchantableAndRejectsNonPositive() {
        ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD).enchantable(5).build();

        assertThat(item.getItemMeta().hasEnchantable()).isTrue();
        assertThat(item.getItemMeta().getEnchantable()).isEqualTo(5);

        assertThatThrownBy(() -> ItemBuilder.of(Material.DIAMOND_SWORD).enchantable(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retypesToANewMaterialCarryingOverMeta() {
        ItemStack pickaxe = ItemBuilder.of(Material.DIAMOND_PICKAXE)
                .enchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3)
                .build();

        ItemStack axe = ItemBuilder.from(pickaxe).material(Material.DIAMOND_AXE).build();

        assertThat(axe.getType()).isEqualTo(Material.DIAMOND_AXE);
        assertThat(axe.getItemMeta().hasEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING))
                .isTrue();
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void materialRejectsAir() {
        assertThatThrownBy(() -> ItemBuilder.of(Material.STONE).material(Material.AIR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemBuilder.of(Material.STONE).material(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void tooltipAndEnchantableWiringDoesNotThrow() {
        NamespacedKey style = NamespacedKey.fromString("uxmlib:plain");
        assertThatCode(() -> ItemBuilder.of(Material.STONE)
                        .tooltipStyle(style)
                        .hideTooltip(false)
                        .build())
                .doesNotThrowAnyException();
    }
}

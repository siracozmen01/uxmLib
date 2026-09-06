package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

class ItemConfigTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ConfigurationNode hocon(String text) throws Exception {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new StringReader(text)))
                .build();
        return loader.load();
    }

    @Test
    void readsMaterialNameAndLore() throws Exception {
        ConfigurationNode node = hocon("""
                material = DIAMOND_SWORD
                name = "<red>Blade"
                lore = ["<gray>line one", "line two"]
                """);

        ItemStack item = ItemConfig.load(node).build();

        assertThat(item.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(Text.plain(item.getItemMeta().displayName())).isEqualTo("Blade");
        assertThat(item.getItemMeta().lore()).hasSize(2);
        assertThat(Text.plain(item.getItemMeta().lore().get(0))).isEqualTo("line one");
    }

    @Test
    @SuppressWarnings("deprecation") // getCustomModelData() is the int read-back; the value round-trips
    void readsAmountEnchantsFlagsAndModelData() throws Exception {
        ConfigurationNode node = hocon("""
                material = DIAMOND_PICKAXE
                amount = 1
                enchants { efficiency = 5, unbreaking = 3 }
                flags = [HIDE_ENCHANTS, HIDE_ATTRIBUTES]
                custom-model-data = 42
                unbreakable = true
                """);

        ItemStack item = ItemConfig.load(node).build();

        assertThat(item.getEnchantmentLevel(Items.enchantment("efficiency"))).isEqualTo(5);
        assertThat(item.getEnchantmentLevel(Items.enchantment("unbreaking"))).isEqualTo(3);
        assertThat(item.getItemMeta().getItemFlags()).contains(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        assertThat(item.getItemMeta().getCustomModelData()).isEqualTo(42);
        assertThat(item.getItemMeta().isUnbreakable()).isTrue();
    }

    @Test
    void appliesGlowWithoutAnEnchant() throws Exception {
        ConfigurationNode node = hocon("""
                material = STONE
                glow = true
                """);

        ItemStack item = ItemConfig.load(node).build();

        assertThat(item.getItemMeta().getEnchantmentGlintOverride()).isTrue();
        assertThat(item.getItemMeta().hasEnchants()).isFalse();
    }

    @Test
    void readsASkullForAPlayerHead() throws Exception {
        ConfigurationNode node = hocon("""
                material = PLAYER_HEAD
                skull = "Notch"
                """);

        ItemStack item = ItemConfig.load(node).build();

        assertThat(item.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(item.getItemMeta()).isInstanceOf(org.bukkit.inventory.meta.SkullMeta.class);
    }

    @Test
    void resolvesPlaceholdersInNameAndLore() throws Exception {
        ConfigurationNode node = hocon("""
                material = PAPER
                name = "Hello <player>"
                lore = ["welcome <player>"]
                """);

        ItemStack item = ItemConfig.load(node, List.of(Text.placeholder("player", "Steve")))
                .build();

        assertThat(Text.plain(item.getItemMeta().displayName())).isEqualTo("Hello Steve");
        assertThat(Text.plain(item.getItemMeta().lore().get(0))).isEqualTo("welcome Steve");
    }

    @Test
    void wrapsLongLoreLinesWhenAWidthIsGiven() throws Exception {
        ConfigurationNode node = hocon("""
                material = PAPER
                lore = ["the quick brown fox jumps over"]
                """);

        ItemStack item = ItemConfig.load(node, List.of(), 12).build();

        assertThat(item.getItemMeta().lore()).hasSize(3);
        assertThat(Text.plain(item.getItemMeta().lore().get(0))).isEqualTo("the quick");
    }

    @Test
    void splitsEmbeddedNewlinesInLore() throws Exception {
        ConfigurationNode node = hocon("""
                material = PAPER
                lore = ["first\\nsecond"]
                """);

        ItemStack item = ItemConfig.load(node).build();

        assertThat(item.getItemMeta().lore()).hasSize(2);
        assertThat(Text.plain(item.getItemMeta().lore().get(1))).isEqualTo("second");
    }

    @Test
    void rejectsAMissingMaterial() throws Exception {
        ConfigurationNode node = hocon("name = \"<red>no material\"\n");

        assertThatThrownBy(() -> ItemConfig.load(node)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownMaterial() throws Exception {
        ConfigurationNode node = hocon("material = NOT_A_REAL_BLOCK\n");

        assertThatThrownBy(() -> ItemConfig.load(node)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultsAmountToOneWhenAbsent() throws Exception {
        ConfigurationNode node = hocon("material = STONE\n");

        ItemStack item = ItemConfig.load(node).build();

        assertThat(item.getAmount()).isEqualTo(1);
    }

    @Test
    void rejectsAnEnchantWithoutANumericLevelWithAConfigAwareMessage() throws Exception {
        ConfigurationNode node = hocon("""
                material = DIAMOND_SWORD
                enchants { sharpness = "five" }
                """);

        assertThatThrownBy(() -> ItemConfig.load(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sharpness")
                .hasMessageContaining("level");
    }

    @Test
    void rejectsAnUnknownEnchantKeyByName() throws Exception {
        ConfigurationNode node = hocon("""
                material = DIAMOND_SWORD
                enchants { not_a_real_enchant = 3 }
                """);

        assertThatThrownBy(() -> ItemConfig.load(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not_a_real_enchant");
    }

    /**
     * The key a menu file actually wants, in place of the deprecated {@code HIDE_ADDITIONAL_TOOLTIP} flag
     * an operator would otherwise reach for. Only the wiring is checked here: MockBukkit accepts a data
     * component and does not keep it, so what the component ends up holding is proven in
     * {@link TooltipsTest} where it can be read back.
     */
    @Test
    void readsTheHideVanillaTooltipKey() throws Exception {
        ConfigurationNode node = hocon("""
                material = LEATHER_CHESTPLATE
                name = "<white>Cosmetics"
                hide-vanilla-tooltip = true
                """);

        ItemStack item = ItemConfig.load(node).build();

        assertThat(item.getType()).isEqualTo(Material.LEATHER_CHESTPLATE);
        assertThat(Text.plain(item.getItemMeta().displayName())).isEqualTo("Cosmetics");
    }
}

package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.StringReader;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The six blocks a plugin had to write for itself: the potion, the firework, the trim, the dyed colour, the
 * banner patterns and the mob inside a spawner.
 *
 * <p>Each one is a round trip. The test writes the block an operator would write, reads it back through
 * {@link ItemConfig}, and compares what came out of the item against what went into the file. A field the
 * reader accepts and does not apply would pass a shallower test and would still make a shop refuse to buy
 * back what it just sold.
 */
class ItemConfigMetaTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a potion carries its base type, its tint and its custom effects")
    void aPotionRoundTrips() throws Exception {
        ItemStack item = load("""
                material = POTION
                potion {
                  type    = strength
                  color   = "#00AAFF"
                  effects = ["speed:1:600", "jump_boost"]
                }
                """);

        PotionMeta meta = (PotionMeta) item.getItemMeta();
        assertThat(meta.getBasePotionType()).isEqualTo(entry(RegistryKey.POTION, "strength"));
        assertThat(meta.getColor()).isEqualTo(Color.fromRGB(0x00AAFF));
        assertThat(meta.getCustomEffects()).hasSize(2);
        assertThat(meta.getCustomEffects().get(0).getType().key().value()).isEqualTo("speed");
        assertThat(meta.getCustomEffects().get(0).getAmplifier()).isEqualTo(1);
        assertThat(meta.getCustomEffects().get(0).getDuration()).isEqualTo(600);
        // A token with no amplifier and no duration takes the documented defaults rather than failing.
        assertThat(meta.getCustomEffects().get(1).getAmplifier()).isZero();
        assertThat(meta.getCustomEffects().get(1).getDuration()).isEqualTo(300);
    }

    @Test
    @DisplayName("a firework carries its flight power and every part of an effect")
    void aFireworkRoundTrips() throws Exception {
        ItemStack item = load("""
                material = FIREWORK_ROCKET
                firework {
                  power   = 2
                  effects = ["ball_large:#ff0000,#ffff00:#ffffff:flicker,trail"]
                }
                """);

        FireworkMeta meta = (FireworkMeta) item.getItemMeta();
        assertThat(meta.getPower()).isEqualTo(2);
        assertThat(meta.getEffects()).hasSize(1);
        FireworkEffect effect = meta.getEffects().get(0);
        assertThat(effect.getType()).isEqualTo(FireworkEffect.Type.BALL_LARGE);
        assertThat(effect.getColors()).containsExactly(Color.fromRGB(0xFF0000), Color.fromRGB(0xFFFF00));
        assertThat(effect.getFadeColors()).containsExactly(Color.fromRGB(0xFFFFFF));
        assertThat(effect.hasFlicker()).isTrue();
        assertThat(effect.hasTrail()).isTrue();
    }

    @Test
    @DisplayName("a firework effect without flags or fade colours is still read")
    void aPlainFireworkEffectRoundTrips() throws Exception {
        ItemStack item = load("""
                material = FIREWORK_ROCKET
                firework { effects = ["burst:red"] }
                """);

        FireworkEffect effect = ((FireworkMeta) item.getItemMeta()).getEffects().get(0);
        assertThat(effect.getType()).isEqualTo(FireworkEffect.Type.BURST);
        assertThat(effect.getColors()).containsExactly(DyeColor.RED.getColor());
        assertThat(effect.getFadeColors()).isEmpty();
        assertThat(effect.hasFlicker()).isFalse();
    }

    @Test
    @DisplayName("armour carries the trim an operator named")
    void aTrimRoundTrips() throws Exception {
        ItemStack item = load("""
                material = DIAMOND_CHESTPLATE
                trim { material = diamond, pattern = sentry }
                """);

        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        assertThat(meta.hasTrim()).isTrue();
        assertThat(meta.getTrim().getMaterial()).isEqualTo(entry(RegistryKey.TRIM_MATERIAL, "diamond"));
        assertThat(meta.getTrim().getPattern()).isEqualTo(entry(RegistryKey.TRIM_PATTERN, "sentry"));
    }

    @Test
    @DisplayName("leather armour carries the colour it was dyed")
    void aDyedColourRoundTrips() throws Exception {
        ItemStack item = load("""
                material = LEATHER_HELMET
                leather-color = "#A1FF33"
                """);

        assertThat(((LeatherArmorMeta) item.getItemMeta()).getColor()).isEqualTo(Color.fromRGB(0xA1FF33));
    }

    @Test
    @DisplayName("a colour may be written as an r,g,b triple or as a dye name")
    void aColourMayBeWrittenThreeWays() throws Exception {
        ItemStack triple = load("material = LEATHER_HELMET\nleather-color = \"12, 34, 56\"\n");
        ItemStack named = load("material = LEATHER_HELMET\nleather-color = lime\n");

        assertThat(((LeatherArmorMeta) triple.getItemMeta()).getColor()).isEqualTo(Color.fromRGB(12, 34, 56));
        assertThat(((LeatherArmorMeta) named.getItemMeta()).getColor()).isEqualTo(DyeColor.LIME.getColor());
    }

    @Test
    @DisplayName("a banner carries its patterns in the order they were written")
    void bannerPatternsRoundTripInOrder() throws Exception {
        ItemStack item = load("""
                material = WHITE_BANNER
                banner { patterns = ["stripe_top:red", "border:white"] }
                """);

        BannerMeta meta = (BannerMeta) item.getItemMeta();
        assertThat(meta.getPatterns()).hasSize(2);
        assertThat(meta.getPatterns().get(0).getColor()).isEqualTo(DyeColor.RED);
        assertThat(meta.getPatterns().get(0).getPattern()).isEqualTo(entry(RegistryKey.BANNER_PATTERN, "stripe_top"));
        assertThat(meta.getPatterns().get(1).getColor()).isEqualTo(DyeColor.WHITE);
        assertThat(meta.getPatterns().get(1).getPattern()).isEqualTo(entry(RegistryKey.BANNER_PATTERN, "border"));
    }

    @Test
    @DisplayName("a shield takes the same banner block")
    void aShieldTakesTheSameBlock() throws Exception {
        ItemStack item = load("""
                material = SHIELD
                banner { patterns = ["border:white"] }
                """);

        assertThat(((BannerMeta) item.getItemMeta()).getPatterns()).hasSize(1);
    }

    @Test
    @DisplayName("a spawner carries the mob inside it")
    void aSpawnerRoundTrips() throws Exception {
        ItemStack item = load("material = SPAWNER\nspawner = zombie\n");

        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        assertThat(meta.getBlockState()).isInstanceOf(CreatureSpawner.class);
        assertThat(((CreatureSpawner) meta.getBlockState()).getSpawnedType()).isEqualTo(EntityType.ZOMBIE);
    }

    @Test
    @DisplayName("an item that names none of the six is unchanged")
    void anItemWithoutTheBlocksIsUntouched() throws Exception {
        ItemStack item = load("material = DIAMOND_SWORD\n");

        assertThat(item.getType()).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    @DisplayName("a potion type the server does not know is refused, and names itself")
    void anUnknownPotionTypeIsRefused() {
        assertThatThrownBy(() -> load("material = POTION\npotion { type = definitely_not_a_potion }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown potion type")
                .hasMessageContaining("definitely_not_a_potion");
    }

    @Test
    @DisplayName("a trim with only half of its pair is refused")
    void ahalfWrittenTrimIsRefused() {
        assertThatThrownBy(() -> load("material = DIAMOND_CHESTPLATE\ntrim { material = diamond }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trim");
    }

    @Test
    @DisplayName("a firework effect with no colour is refused, rather than thrown by Bukkit later")
    void aColourlessFireworkEffectIsRefused() {
        assertThatThrownBy(() -> load("material = FIREWORK_ROCKET\nfirework { effects = [\"ball\"] }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one colour");
    }

    @Test
    @DisplayName("an unknown firework flag is refused and says what may be written")
    void anUnknownFireworkFlagIsRefused() {
        assertThatThrownBy(() -> load("material = FIREWORK_ROCKET\nfirework { effects = [\"ball:red::sparkle\"] }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flicker or trail");
    }

    @Test
    @DisplayName("a banner pattern missing its dye colour is refused")
    void aPatternWithoutADyeIsRefused() {
        assertThatThrownBy(() -> load("material = WHITE_BANNER\nbanner { patterns = [\"stripe_top\"] }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pattern:dyecolor");
    }

    @Test
    @DisplayName("a colour that is not a colour is refused, and names the key it was under")
    void aBadColourIsRefused() {
        assertThatThrownBy(() -> load("material = LEATHER_HELMET\nleather-color = \"#zzzzzz\"\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leather-color");
    }

    @Test
    @DisplayName("a spawner mob the server does not know is refused")
    void anUnknownSpawnerMobIsRefused() {
        assertThatThrownBy(() -> load("material = SPAWNER\nspawner = not_a_mob\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown spawner mob");
    }

    private static <T extends org.bukkit.Keyed> T entry(RegistryKey<T> key, String id) {
        return RegistryAccess.registryAccess().getRegistry(key).getOrThrow(NamespacedKey.minecraft(id));
    }

    private static ItemStack load(String text) throws Exception {
        return ItemConfig.load(hocon(text)).build();
    }

    private static ConfigurationNode hocon(String text) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(text)))
                .build()
                .load();
    }
}

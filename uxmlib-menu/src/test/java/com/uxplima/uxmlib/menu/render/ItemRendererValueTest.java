package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The scalar values an operator writes inside a {@code decor} block: a colour, a flag name, a potion token. Every
 * one of them is parsed from a hand-written string, and every one of them degrades quietly when it does not parse,
 * so these tests pin what the quiet degradation actually is.
 *
 * <p>Each parser is driven twice on purpose. The first test of a pair puts a well-formed value in and reads the
 * applied result back off the stack. That is not a duplicate of the fail-soft test below it: without it, a runtime
 * that models none of this would let every degrade-to-nothing assertion pass for the wrong reason, because nothing
 * would ever have been applied in the first place. The good value is the evidence that the bad value is what was
 * rejected.
 */
class ItemRendererValueTest {

    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text("catalogue(" + key + ")");
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private ItemRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        renderer = new ItemRenderer(new PlainText(), Theme::defaults, placeholders);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuItemSpec item(String hocon) {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { one { slot = 0, " + hocon + " } }");
        return Objects.requireNonNull(spec.items().get("one"));
    }

    private ItemStack render(String hocon) {
        return renderer.render(item(hocon), MenuContext.of(viewer, null, 0));
    }

    /** A leather helmet carrying the given {@code leather-color} token, and nothing else. */
    private ItemStack leather(String token) {
        return render("material = LEATHER_HELMET, name = \"n\", decor { leather-color = \"" + token + "\" }");
    }

    private static Color colorOf(ItemStack stack) {
        return ((LeatherArmorMeta) Objects.requireNonNull(stack.getItemMeta())).getColor();
    }

    private Color untouchedLeather() {
        return colorOf(render("material = LEATHER_HELMET, name = \"n\""));
    }

    private static List<PotionEffect> effectsOf(ItemStack stack) {
        return ((PotionMeta) Objects.requireNonNull(stack.getItemMeta())).getCustomEffects();
    }

    // -- a colour: hex ----------------------------------------------------------------------------------------

    @Test
    void aHexColourReachesTheStack() {
        assertThat(colorOf(leather("#A1FF33"))).isEqualTo(Color.fromRGB(0xA1FF33));
    }

    @Test
    void aHexWithANonHexDigitLeavesTheColourAlone() {
        assertThat(colorOf(leather("#GGGGGG"))).isEqualTo(untouchedLeather());
    }

    @Test
    void aHexWiderThanThreeChannelsIsOutOfRangeAndLeavesTheColourAlone() {
        assertThat(colorOf(leather("#1A1FF33"))).isEqualTo(untouchedLeather());
    }

    /**
     * The hex branch parses with a radix, and a radix parse accepts a leading sign. The value it then produces is
     * negative, which no colour channel can hold, so the range check behind {@code fromRGB} is what refuses it. The
     * guard is the constructor, not the parser, and this pins that the constructor is in fact reached.
     */
    @Test
    void aSignedHexIsRefusedByTheColourRangeRatherThanByTheParse() {
        assertThat(colorOf(leather("#-1"))).isEqualTo(untouchedLeather());
    }

    // -- a colour: an r,g,b triple ----------------------------------------------------------------------------

    @Test
    void aThreeChannelTripleReachesTheStack() {
        assertThat(colorOf(leather("161,255,51"))).isEqualTo(Color.fromRGB(0xA1FF33));
    }

    @Test
    void spacesAroundEachChannelAreTrimmed() {
        assertThat(colorOf(leather(" 161 , 255 , 51 "))).isEqualTo(Color.fromRGB(0xA1FF33));
    }

    @Test
    void aTripleMissingAChannelLeavesTheColourAlone() {
        assertThat(colorOf(leather("161,255"))).isEqualTo(untouchedLeather());
    }

    @Test
    void aFourthChannelIsNotAnAlphaAndLeavesTheColourAlone() {
        assertThat(colorOf(leather("161,255,51,128"))).isEqualTo(untouchedLeather());
    }

    @Test
    void aChannelAboveTwoHundredAndFiftyFiveLeavesTheColourAlone() {
        assertThat(colorOf(leather("300,0,0"))).isEqualTo(untouchedLeather());
    }

    @Test
    void aChannelThatIsNotANumberLeavesTheColourAlone() {
        assertThat(colorOf(leather("161,green,51"))).isEqualTo(untouchedLeather());
    }

    // -- a colour: a dye name ---------------------------------------------------------------------------------

    @Test
    void aNamedDyeReachesTheStack() {
        assertThat(colorOf(leather("RED"))).isEqualTo(DyeColor.RED.getColor());
    }

    @Test
    void aNamedDyeIsReadWithoutRegardToCase() {
        assertThat(colorOf(leather("red"))).isEqualTo(DyeColor.RED.getColor());
    }

    @Test
    void aColourNameThatIsNotADyeLeavesTheColourAlone() {
        assertThat(colorOf(leather("burgundy"))).isEqualTo(untouchedLeather());
    }

    // -- a potion effect token --------------------------------------------------------------------------------

    @Test
    void anEffectWithItsAmplifierAndDurationReachesTheStack() {
        List<PotionEffect> effects =
                effectsOf(render("material = POTION, name = \"n\", decor { potion { effects = [\"speed:2:900\"] } }"));

        assertThat(effects).singleElement().satisfies(effect -> {
            assertThat(effect.getAmplifier()).isEqualTo(2);
            assertThat(effect.getDuration()).isEqualTo(900);
        });
    }

    @Test
    void anEffectNamingNoAmplifierOrDurationTakesTheDefaultsRatherThanNothing() {
        List<PotionEffect> effects =
                effectsOf(render("material = POTION, name = \"n\", decor { potion { effects = [\"speed\"] } }"));

        assertThat(effects).singleElement().satisfies(effect -> {
            assertThat(effect.getAmplifier()).isZero();
            assertThat(effect.getDuration()).isEqualTo(600);
        });
    }

    /** A negative amplifier is clamped rather than refused, because level zero is a level and minus one is not. */
    @Test
    void aNegativeAmplifierIsClampedToZero() {
        List<PotionEffect> effects =
                effectsOf(render("material = POTION, name = \"n\", decor { potion { effects = [\"speed:-3:600\"] } }"));

        assertThat(effects).singleElement().satisfies(effect -> assertThat(effect.getAmplifier())
                .isZero());
    }

    @Test
    void anAmplifierThatIsNotANumberFallsBackToZeroRatherThanDroppingTheEffect() {
        List<PotionEffect> effects = effectsOf(
                render("material = POTION, name = \"n\", decor { potion { effects = [\"speed:strong:600\"] } }"));

        assertThat(effects).singleElement().satisfies(effect -> {
            assertThat(effect.getAmplifier()).isZero();
            assertThat(effect.getDuration()).isEqualTo(600);
        });
    }

    @Test
    void aDurationThatIsNotANumberFallsBackToTheDefaultRatherThanToZero() {
        List<PotionEffect> effects =
                effectsOf(render("material = POTION, name = \"n\", decor { potion { effects = [\"speed:1:soon\"] } }"));

        assertThat(effects).singleElement().satisfies(effect -> assertThat(effect.getDuration())
                .isEqualTo(600));
    }

    @Test
    void anEffectNameThatIsNotAnEffectIsDroppedAndTheRestAreKept() {
        List<PotionEffect> effects = effectsOf(render(
                "material = POTION, name = \"n\", decor { potion { effects = [\"levitation_2\", \"speed:1:600\"] } }"));

        assertThat(effects).singleElement().satisfies(effect -> assertThat(effect.getDuration())
                .isEqualTo(600));
    }

    // -- a flag name ------------------------------------------------------------------------------------------

    @Test
    void aFlagNameReachesTheStack() {
        ItemStack stack = render("material = STONE, name = \"n\", decor { flags = [\"HIDE_ATTRIBUTES\"] }");

        assertThat(Objects.requireNonNull(stack.getItemMeta()).hasItemFlag(ItemFlag.HIDE_ATTRIBUTES))
                .isTrue();
    }

    /**
     * Every other enum-valued token in this grammar is read without regard to case, so a file naming a flag in the
     * lower case the rest of the block uses has to mean the flag it names. A dropped flag looks exactly like a
     * server default, which is why this went unnoticed: the icon renders, it just renders with the tooltip section
     * the operator asked to hide.
     */
    @Test
    void aFlagNameIsReadWithoutRegardToCaseLikeEveryOtherNameInTheBlock() {
        ItemStack stack = render("material = STONE, name = \"n\", decor { flags = [\"hide_attributes\"] }");

        assertThat(Objects.requireNonNull(stack.getItemMeta()).hasItemFlag(ItemFlag.HIDE_ATTRIBUTES))
                .isTrue();
    }

    @Test
    void spacesAroundAFlagNameAreTrimmed() {
        ItemStack stack = render("material = STONE, name = \"n\", decor { flags = [\" HIDE_ATTRIBUTES \"] }");

        assertThat(Objects.requireNonNull(stack.getItemMeta()).hasItemFlag(ItemFlag.HIDE_ATTRIBUTES))
                .isTrue();
    }

    @Test
    void aTokenThatIsNotAFlagIsDroppedAndTheFlagsBesideItAreKept() {
        ItemStack stack =
                render("material = STONE, name = \"n\", decor { flags = [\"HIDE_NOTHING\", \"HIDE_ATTRIBUTES\"] }");

        assertThat(Objects.requireNonNull(stack.getItemMeta()).hasItemFlag(ItemFlag.HIDE_ATTRIBUTES))
                .isTrue();
    }
}

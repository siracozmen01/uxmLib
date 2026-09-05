package com.uxplima.uxmlib.menu.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The selector a property opens, and what it hands the engine. The property paints nothing itself: it builds one
 * prepared button per option and gives them to the opener, so everything worth asserting is in that handoff.
 */
class EnumPropertyTest {

    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text(key);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** Records the one selector the property opens, so the handoff can be read back. */
    private static final class RecordingOpener implements SelectorOpener {

        @Nullable Component title;

        int rows;

        @Nullable Material filler;

        List<SelectorButton> buttons = List.of();

        int opens;

        @Override
        public void openSelector(
                Player viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
            opens++;
            this.title = title;
            this.rows = rows;
            this.filler = filler;
            this.buttons = buttons;
        }
    }

    private final SameThreadScheduler scheduler = new SameThreadScheduler();

    private final RecordingOpener opener = new RecordingOpener();

    private final AtomicReference<String> value = new AtomicReference<>("mid");

    private final List<String> written = new ArrayList<>();

    private int reopens;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnumProperty<String> property(List<String> options, List<Integer> slots) {
        return new EnumProperty<>(
                "label",
                "menu.selector.title",
                Material.PAPER,
                new PlainText(),
                options,
                value::get,
                (who, option) -> "shown:" + option,
                next -> {
                    written.add(next);
                    value.set(next);
                },
                Material.STONE,
                Material.GRAY_STAINED_GLASS_PANE,
                slots,
                3,
                scheduler);
    }

    private PropertyClick click() {
        return new PropertyClick(viewer, false, false, () -> reopens++, opener, (who, title, onYes, onNo) -> {
            throw new UnsupportedOperationException("an enum property opens no confirm");
        });
    }

    private static boolean glints(SelectorButton button) {
        return Objects.requireNonNull(button.icon().getItemMeta()).hasEnchants();
    }

    private static String nameOf(SelectorButton button) {
        Component name = button.icon().getItemMeta().displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    @Test
    void aClickOpensOneSelectorRatherThanSteppingTheValue() {
        property(List.of("low", "mid", "high"), List.of(1, 2, 3)).onClick(click());

        assertThat(opener.opens).isEqualTo(1);
        assertThat(written).isEmpty();
        assertThat(reopens).isZero();
    }

    @Test
    void theGeometryAndTheTitleComeFromTheCallerAndNotFromTheProperty() {
        property(List.of("low", "mid"), List.of(1, 2)).onClick(click());

        assertThat(opener.rows).isEqualTo(3);
        assertThat(opener.filler).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(opener.title)))
                .as("the title is a catalogue key resolved for the viewer, never a literal")
                .isEqualTo("menu.selector.title");
    }

    @Test
    void oneButtonPerOptionLandsInTheSlotsTheLayoutNamedInOrder() {
        property(List.of("low", "mid", "high"), List.of(4, 0, 8)).onClick(click());

        assertThat(opener.buttons).extracting(SelectorButton::slot).containsExactly(4, 0, 8);
        assertThat(opener.buttons)
                .extracting(EnumPropertyTest::nameOf)
                .containsExactly("<value>shown:low</value>", "<value>shown:mid</value>", "<value>shown:high</value>");
    }

    @Test
    void theLiveOptionGlintsAndTheOthersDoNot() {
        property(List.of("low", "mid", "high"), List.of(1, 2, 3)).onClick(click());

        assertThat(opener.buttons).extracting(EnumPropertyTest::glints).containsExactly(false, true, false);
    }

    /**
     * A stored value the option list no longer holds glints nothing. The selector still opens with every option, so
     * the next click puts the value back on the list, which is the only way out of a drifted state.
     */
    @Test
    void aValueOutsideTheOptionListGlintsNothingAndStillOffersEveryOption() {
        value.set("something-else");

        property(List.of("low", "mid", "high"), List.of(1, 2, 3)).onClick(click());

        assertThat(opener.buttons).hasSize(3);
        assertThat(opener.buttons).extracting(EnumPropertyTest::glints).containsOnly(false);
    }

    /**
     * More options than slots draws the ones that fit and drops the rest, silently. That is the fail-soft choice and
     * it is worth stating: the layout is an operator's file and a short slot list is a layout mistake rather than a
     * code one, but nothing tells them, so a selector quietly missing its tail is the shape to expect.
     */
    @Test
    void moreOptionsThanSlotsDrawsTheOnesThatFitAndDropsTheTail() {
        property(List.of("low", "mid", "high"), List.of(1, 2)).onClick(click());

        assertThat(opener.buttons)
                .extracting(EnumPropertyTest::nameOf)
                .containsExactly("<value>shown:low</value>", "<value>shown:mid</value>");
    }

    @Test
    void moreSlotsThanOptionsLeavesTheSpareSlotsToTheFiller() {
        property(List.of("low"), List.of(1, 2, 3)).onClick(click());

        assertThat(opener.buttons).hasSize(1);
    }

    @Test
    void choosingAnOptionWritesOffTheTickThreadAndRedrawsBack() {
        property(List.of("low", "mid", "high"), List.of(1, 2, 3)).onClick(click());

        opener.buttons.get(2).onClick().onClick(false, false);

        assertThat(written).containsExactly("high");
        assertThat(scheduler.asyncHops).isEqualTo(1);
        assertThat(scheduler.entityHops).isEqualTo(1);
        assertThat(reopens).isEqualTo(1);
    }

    /** An option button is single-gesture: a right or shift click chooses it exactly as a left click does. */
    @Test
    void anOptionIsChosenByAnyGestureBecauseThereIsNothingElseAClickCouldMean() {
        property(List.of("low", "mid", "high"), List.of(1, 2, 3)).onClick(click());

        opener.buttons.get(0).onClick().onClick(true, true);

        assertThat(written).containsExactly("low");
    }

    @Test
    void aPerOptionIconFunctionDecidesTheMaterialAndNothingElse() {
        EnumProperty<String> property = new EnumProperty<>(
                "label",
                "menu.selector.title",
                Material.PAPER,
                new PlainText(),
                List.of("low", "high"),
                value::get,
                (who, option) -> "shown:" + option,
                written::add,
                Material.STONE,
                option -> option.equals("low") ? Material.DIRT : Material.DIAMOND,
                Material.GRAY_STAINED_GLASS_PANE,
                List.of(1, 2),
                3,
                scheduler);
        value.set("high");

        property.onClick(click());

        assertThat(opener.buttons)
                .extracting(button -> button.icon().getType())
                .containsExactly(Material.DIRT, Material.DIAMOND);
        assertThat(opener.buttons)
                .as("the function decides the material; the name and the glint are unchanged either way")
                .extracting(EnumPropertyTest::glints)
                .containsExactly(false, true);
    }

    @Test
    void theValueLoreComesFromTheDisplayFunctionAndNotFromTheOptionsOwnName() {
        assertThat(property(List.of("low", "mid"), List.of(1, 2)).valueLore(viewer))
                .isEqualTo("shown:mid");
    }

    @Test
    void aSelectorWithNoOptionsOrNoSlotsIsRefusedAtWiringTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> property(List.of(), List.of(1)))
                .withMessageContaining("at least one option");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> property(List.of("low"), List.of()))
                .withMessageContaining("optionSlots must not be empty");
    }

    @Test
    void aWindowThatIsNotOneToSixRowsIsRefused() {
        for (int rows : List.of(0, 7)) {
            int declared = rows;
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new EnumProperty<>(
                            "label",
                            "menu.selector.title",
                            Material.PAPER,
                            new PlainText(),
                            List.of("low"),
                            value::get,
                            (who, option) -> option,
                            written::add,
                            Material.STONE,
                            Material.GRAY_STAINED_GLASS_PANE,
                            List.of(1),
                            declared,
                            scheduler))
                    .withMessageContaining("rows must be 1..6");
        }
    }

    @Test
    void aPreparedIconIsWhatTheOpenerGetsSoTheEngineMakesNoPresentationChoice() {
        property(List.of("low"), List.of(1)).onClick(click());

        ItemStack icon = opener.buttons.get(0).icon();

        assertThat(icon.getType()).isEqualTo(Material.STONE);
        assertThat(nameOf(opener.buttons.get(0)))
                .as(
                        "an option name is wrapped in the value token so it picks up the canon accent, not a literal colour")
                .isEqualTo("<value>shown:low</value>");
    }
}

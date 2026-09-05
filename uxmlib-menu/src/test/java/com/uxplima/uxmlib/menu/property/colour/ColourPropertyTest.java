package com.uxplima.uxmlib.menu.property.colour;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.property.SelectorButton;
import com.uxplima.uxmlib.menu.property.SelectorOpener;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The colour picker: a palette of the sixteen named colours, a custom-hex button, a clear button and a back button,
 * handed to the engine as prepared buttons. The property paints nothing itself, so the handoff is where everything
 * worth asserting is, exactly as the enum picker's is.
 *
 * <p>The picker is the only property with two ways to reach the same setter, a swatch and a typed hex line, and one
 * way to reach a different one, the clear button. Those three are what the tests separate: which callback fires, and
 * whether anything is written when the typed line is not a colour.
 */
class ColourPropertyTest {

    /** A catalogue that hands every key straight back, so a rendered name is readable in an assertion. */
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

    /** The sentinel this property's owner uses for "no override"; any value the palette cannot produce would do. */
    private static final int NO_OVERRIDE = 0;

    private static final int OPAQUE_RED = 0xFFFF0000;

    private final SameThreadScheduler scheduler = new SameThreadScheduler();

    private final RecordingOpener opener = new RecordingOpener();

    private final AtomicInteger value = new AtomicInteger(NO_OVERRIDE);

    private final List<Integer> written = new ArrayList<>();

    private final List<InputRequest> prompted = new ArrayList<>();

    private int cleared;

    private int reopens;

    /** The line the fake prompt submits, or null to make it cancel instead. */
    private @Nullable String typed;

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

    private ColourProperty property() {
        return new ColourProperty(
                "label",
                Material.PAPER,
                value::get,
                next -> {
                    written.add(next);
                    value.set(next);
                },
                () -> {
                    cleared++;
                    value.set(NO_OVERRIDE);
                },
                NO_OVERRIDE,
                who -> "none",
                new PlainText(),
                ColourPickerText.shared(),
                ColourPickerLayout.codeDefault(),
                (who, request, onSubmit, onCancel) -> {
                    prompted.add(request);
                    if (typed == null) {
                        onCancel.run();
                    } else {
                        onSubmit.accept(typed);
                    }
                },
                scheduler);
    }

    private PropertyClick click() {
        return new PropertyClick(viewer, false, false, () -> reopens++, opener, (who, title, onYes, onNo) -> {
            throw new UnsupportedOperationException("a colour property opens no confirm");
        });
    }

    private static boolean glints(SelectorButton button) {
        return Objects.requireNonNull(button.icon().getItemMeta()).hasEnchants();
    }

    private static String nameOf(SelectorButton button) {
        Component name = button.icon().getItemMeta().displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    /** Press the button at {@code slot} with a plain left-click, the only gesture a picker button branches on. */
    private void press(int slot) {
        buttonAt(slot).onClick().onClick(false, false);
    }

    private SelectorButton buttonAt(int slot) {
        return opener.buttons.stream()
                .filter(button -> button.slot() == slot)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no button at slot " + slot));
    }

    // -- the value the property reports --------------------------------------------------------------------

    @Test
    void theSentinelReadsAsTheCallersOwnWordForNoOverrideAndNotAsAColour() {
        value.set(NO_OVERRIDE);

        assertThat(property().valueLore(viewer)).isEqualTo("none");
    }

    /** A fully opaque colour drops its alpha byte: an operator types six digits, so six is what they are shown. */
    @Test
    void anOpaqueColourIsShownAsSixDigits() {
        value.set(OPAQUE_RED);

        assertThat(property().valueLore(viewer)).isEqualTo("#FF0000");
    }

    /** Anything less than fully opaque keeps its alpha, because dropping it would show a different colour. */
    @Test
    void aTranslucentColourKeepsItsAlphaByte() {
        value.set(0x80FF0000);

        assertThat(property().valueLore(viewer)).isEqualTo("#80FF0000");
    }

    @Test
    void aColourWhoseDigitsAreShortIsPaddedRatherThanPrintedShort() {
        value.set(0xFF000102);

        assertThat(property().valueLore(viewer)).isEqualTo("#000102");
    }

    // -- what a click hands the engine ---------------------------------------------------------------------

    @Test
    void aClickOpensOnePickerRatherThanChangingTheColour() {
        property().onClick(click());

        assertThat(opener.opens).isEqualTo(1);
        assertThat(written).isEmpty();
        assertThat(cleared).isZero();
    }

    @Test
    void theGeometryAndTheTitleComeFromTheLayoutAndTheCatalogue() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();

        property().onClick(click());

        assertThat(opener.rows).isEqualTo(layout.rows());
        assertThat(opener.filler).isEqualTo(layout.filler());
        assertThat(PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(opener.title)))
                .as("the title is a catalogue key resolved for the viewer, never a literal")
                .isEqualTo(ColourPickerText.shared().title());
    }

    /** One button per swatch at the layout's slot for it, plus the three chrome buttons and nothing else. */
    @Test
    void everySwatchGetsAButtonAtItsLayoutSlotAndTheChromeGetsThree() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();

        property().onClick(click());

        assertThat(opener.buttons).hasSize(ColourSwatch.palette().size() + 3);
        assertThat(opener.buttons.subList(0, layout.paletteSlots().size()))
                .extracting(SelectorButton::slot)
                .containsExactlyElementsOf(layout.paletteSlots());
        assertThat(buttonAt(layout.customSlot()).icon().getType()).isEqualTo(layout.customIcon());
        assertThat(buttonAt(layout.clearSlot()).icon().getType()).isEqualTo(layout.clearIcon());
        assertThat(buttonAt(layout.backSlot()).icon().getType()).isEqualTo(layout.backIcon());
    }

    @Test
    void theLiveColourGlintsAndTheOtherSwatchesDoNot() {
        ColourSwatch second = ColourSwatch.palette().get(1);
        value.set(second.argb());
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();

        property().onClick(click());

        assertThat(glints(buttonAt(layout.paletteSlots().get(1)))).isTrue();
        assertThat(glints(buttonAt(layout.paletteSlots().get(0)))).isFalse();
    }

    /** The sentinel is not a palette colour, so nothing glints: the picker shows no swatch as chosen. */
    @Test
    void withNoOverrideSetNoSwatchGlints() {
        value.set(NO_OVERRIDE);
        int palette = ColourPickerLayout.codeDefault().paletteSlots().size();

        property().onClick(click());

        assertThat(opener.buttons.subList(0, palette))
                .extracting(ColourPropertyTest::glints)
                .containsOnly(false);
    }

    @Test
    void everySwatchIsNamedByItsCatalogueKeyRatherThanByAHexString() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();

        property().onClick(click());

        assertThat(nameOf(buttonAt(layout.paletteSlots().get(0))))
                .isEqualTo(ColourSwatch.palette().get(0).nameKey());
    }

    // -- what each button does -----------------------------------------------------------------------------

    @Test
    void pressingASwatchWritesItsColourAndReopensTheEditor() {
        ColourSwatch third = ColourSwatch.palette().get(2);
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        property().onClick(click());

        press(layout.paletteSlots().get(2));

        assertThat(written).containsExactly(third.argb());
        assertThat(reopens).isEqualTo(1);
        assertThat(cleared).isZero();
    }

    /** The write goes off the tick thread and the reopen comes back onto the viewer's, in that order. */
    @Test
    void theWriteCrossesToTheAsyncThreadAndTheRedrawCrossesBack() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        property().onClick(click());
        int hopsBefore = scheduler.asyncHops;

        press(layout.paletteSlots().get(0));

        assertThat(scheduler.asyncHops)
                .as("the setter runs off the tick thread")
                .isEqualTo(hopsBefore + 1);
        assertThat(scheduler.entityHops)
                .as("and the redraw comes back to the viewer's")
                .isPositive();
    }

    @Test
    void pressingClearFiresTheClearHandlerAndNotTheSetter() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        value.set(OPAQUE_RED);
        property().onClick(click());

        press(layout.clearSlot());

        assertThat(cleared).isEqualTo(1);
        assertThat(written).isEmpty();
        assertThat(reopens).isEqualTo(1);
    }

    @Test
    void pressingBackReopensTheEditorAndChangesNothing() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        property().onClick(click());

        press(layout.backSlot());

        assertThat(reopens).isEqualTo(1);
        assertThat(written).isEmpty();
        assertThat(cleared).isZero();
    }

    // -- the typed hex line --------------------------------------------------------------------------------

    @Test
    void pressingCustomOpensAPromptRatherThanWritingAnything() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        typed = null;
        property().onClick(click());

        press(layout.customSlot());

        assertThat(prompted).hasSize(1);
        assertThat(prompted.get(0).label()).isEqualTo(ColourPickerText.shared().customPrompt());
        assertThat(written).isEmpty();
    }

    /** Cancelling the prompt puts the viewer back in the picker, so a mis-click is one press from being undone. */
    @Test
    void cancellingThePromptReopensThePickerAndWritesNothing() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        typed = null;
        property().onClick(click());
        int opensBefore = opener.opens;

        press(layout.customSlot());

        assertThat(opener.opens).isEqualTo(opensBefore + 1);
        assertThat(written).isEmpty();
        assertThat(reopens).as("the editor is not reopened: the picker is").isZero();
    }

    @Test
    void aValidSixDigitLineIsWrittenAsAnOpaqueColour() {
        ColourProperty property = property();
        property.onClick(click());

        property.applyCustom(click(), "#FF0000");

        assertThat(written).containsExactly(OPAQUE_RED);
        assertThat(reopens).isEqualTo(1);
    }

    @Test
    void aValidEightDigitLineKeepsTheAlphaItWasGiven() {
        ColourProperty property = property();

        property.applyCustom(click(), "#80FF0000");

        assertThat(written).containsExactly(0x80FF0000);
    }

    /**
     * A line that is not a colour writes nothing and re-opens the picker. It does not reopen the editor, because
     * dropping the viewer back into the parent would lose the thing they were half-way through doing.
     */
    @Test
    void aLineThatIsNotAColourReopensThePickerAndWritesNothing() {
        ColourProperty property = property();
        int opensBefore = opener.opens;

        property.applyCustom(click(), "not a colour");

        assertThat(written).isEmpty();
        assertThat(opener.opens).isEqualTo(opensBefore + 1);
        assertThat(reopens).isZero();
    }

    @Test
    void aTypedLineIsWrittenThroughTheSameHopsASwatchIs() {
        ColourProperty property = property();
        int hopsBefore = scheduler.asyncHops;

        property.applyCustom(click(), "#00FF00");

        assertThat(scheduler.asyncHops).isEqualTo(hopsBefore + 1);
    }
}

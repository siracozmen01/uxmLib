package com.uxplima.uxmlib.menu.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The list sub-menu: one gesture-aware button per entry, plus add and back. Every mutation rewrites the whole list
 * through one setter and reopens, so what is worth pinning is which gesture produces which list, and what happens at
 * the two ends where a move has nowhere to go.
 *
 * <p>The buttons are built from a snapshot taken when the sub-menu opened, so an index can outlive the entry it named.
 * The guards for that are asserted rather than assumed: a stale index must leave the list alone, not throw and not
 * write a shorter one.
 */
class ListPropertyTest {

    /** A catalogue that hands every key back with its placeholders filled, so a rendered line is readable. */
    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            String out = key;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return Component.text(out);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** Records the one selector the property opens, so the handoff can be read back. */
    private static final class RecordingOpener implements SelectorOpener {

        List<SelectorButton> buttons = List.of();

        int opens;

        @Override
        public void openSelector(
                Player viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
            opens++;
            this.buttons = buttons;
        }
    }

    /** Records a confirm and lets a test answer it either way, since a removal is gated behind one. */
    private static final class RecordingConfirm implements ConfirmOpener {

        @Nullable Runnable onYes;

        @Nullable Runnable onNo;

        int opens;

        @Override
        public void openConfirm(Player viewer, Component title, Runnable onYes, Runnable onNo) {
            opens++;
            this.onYes = onYes;
            this.onNo = onNo;
        }
    }

    private static final List<Integer> ENTRY_SLOTS = List.of(10, 11, 12, 13);

    private final SameThreadScheduler scheduler = new SameThreadScheduler();

    private final RecordingOpener opener = new RecordingOpener();

    private final RecordingConfirm confirms = new RecordingConfirm();

    private final AtomicReference<List<String>> value = new AtomicReference<>(List.of("one", "two", "three"));

    private final List<List<String>> written = new ArrayList<>();

    private final List<InputRequest> prompted = new ArrayList<>();

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

    private ListProperty property() {
        return new ListProperty(
                "editor.list-line",
                "label",
                Material.PAPER,
                new PlainText(),
                Theme::defaults,
                value::get,
                next -> {
                    written.add(next);
                    value.set(next);
                },
                new ListPropertyText(
                        "list.title",
                        "entry: %entry%",
                        "list.hints",
                        "list.add",
                        "list.add-prompt",
                        "list.edit-prompt",
                        "list.remove-confirm",
                        "list.back"),
                new ListPropertyLayout(
                        3,
                        ENTRY_SLOTS,
                        22,
                        26,
                        Material.PAPER,
                        Material.EMERALD,
                        Material.ARROW,
                        Material.BLACK_STAINED_GLASS_PANE),
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
        return new PropertyClick(viewer, false, false, () -> reopens++, opener, confirms);
    }

    private SelectorButton buttonAt(int slot) {
        return opener.buttons.stream()
                .filter(button -> button.slot() == slot)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no button at slot " + slot));
    }

    /** Press the button at {@code slot} with the given gesture, the way the engine routes a click to one. */
    private void press(int slot, boolean rightClick, boolean shiftClick) {
        buttonAt(slot).onClick().onClick(rightClick, shiftClick);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // -- what the property reports and opens ---------------------------------------------------------------

    /** The property's own lore is the count, because the lines themselves do not fit on a button. */
    @Test
    void theValueLineIsHowManyEntriesThereAre() {
        assertThat(property().valueLore(viewer)).isEqualTo("3");
    }

    @Test
    void anEmptyListStillReportsACountRatherThanNothing() {
        value.set(List.of());

        assertThat(property().valueLore(viewer)).isEqualTo("0");
    }

    @Test
    void aClickOpensOneSubMenuRatherThanChangingTheList() {
        property().onClick(click());

        assertThat(opener.opens).isEqualTo(1);
        assertThat(written).isEmpty();
    }

    @Test
    void oneButtonPerEntryLandsInTheLayoutsSlotsInOrderPlusAddAndBack() {
        property().onClick(click());

        assertThat(opener.buttons).extracting(SelectorButton::slot).containsExactly(10, 11, 12, 22, 26);
        assertThat(buttonAt(22).icon().getType()).isEqualTo(Material.EMERALD);
        assertThat(buttonAt(26).icon().getType()).isEqualTo(Material.ARROW);
    }

    /** The entry's text goes in the lore, not the display name, the same rule every tile in the canon follows. */
    @Test
    void anEntrysTextIsCarriedInItsLoreAndNotItsDisplayName() {
        property().onClick(click());

        List<Component> lore = buttonAt(10).icon().getItemMeta().lore();
        assertThat(lore).isNotNull();
        assertThat(lore.stream().map(ListPropertyTest::plain)).anyMatch(line -> line.contains("entry: one"));
    }

    @Test
    void anEmptyListOpensWithNothingButAddAndBack() {
        value.set(List.of());

        property().onClick(click());

        assertThat(opener.buttons).extracting(SelectorButton::slot).containsExactly(22, 26);
    }

    // -- reordering ----------------------------------------------------------------------------------------

    @Test
    void aLeftClickMovesTheLineUp() {
        property().onClick(click());

        press(11, false, false);

        assertThat(written).containsExactly(List.of("two", "one", "three"));
    }

    @Test
    void aRightClickMovesTheLineDown() {
        property().onClick(click());

        press(11, true, false);

        assertThat(written).containsExactly(List.of("one", "three", "two"));
    }

    /** The first line has nowhere to go up. It reopens unchanged rather than writing the same list back. */
    @Test
    void movingTheFirstLineUpWritesNothing() {
        property().onClick(click());
        int opensBefore = opener.opens;

        press(10, false, false);

        assertThat(written).isEmpty();
        assertThat(opener.opens).isEqualTo(opensBefore + 1);
    }

    @Test
    void movingTheLastLineDownWritesNothing() {
        property().onClick(click());
        int opensBefore = opener.opens;

        press(12, true, false);

        assertThat(written).isEmpty();
        assertThat(opener.opens).isEqualTo(opensBefore + 1);
    }

    /**
     * A button is built from the list as it was when the sub-menu opened, so its index can outlive the entry. A stale
     * index must leave the list alone: the alternative is an exception in a click handler, or a write that drops a
     * line the viewer never asked about.
     */
    @Test
    void aButtonWhoseEntryHasSinceBeenRemovedMovesNothing() {
        property().onClick(click());
        value.set(List.of("one"));

        press(12, false, false);

        assertThat(written).isEmpty();
        assertThat(value.get()).containsExactly("one");
    }

    // -- adding --------------------------------------------------------------------------------------------

    @Test
    void theAddButtonOpensAPromptRatherThanWritingAnything() {
        typed = null;
        property().onClick(click());

        press(22, false, false);

        assertThat(prompted).hasSize(1);
        assertThat(prompted.get(0).label()).isEqualTo("list.add-prompt");
        assertThat(written).isEmpty();
    }

    @Test
    void aSubmittedLineIsAppendedToTheEnd() {
        ListProperty property = property();

        property.applyAdd(click(), "four");

        assertThat(written).containsExactly(List.of("one", "two", "three", "four"));
    }

    /** A blank line is not an entry. It reopens the list rather than adding a line nobody can see or click. */
    @Test
    void aBlankSubmittedLineAddsNothingAndReopensTheList() {
        ListProperty property = property();
        int opensBefore = opener.opens;

        property.applyAdd(click(), "   ");

        assertThat(written).isEmpty();
        assertThat(opener.opens).isEqualTo(opensBefore + 1);
    }

    @Test
    void cancellingTheAddPromptReopensTheListAndWritesNothing() {
        typed = null;
        property().onClick(click());
        int opensBefore = opener.opens;

        press(22, false, false);

        assertThat(opener.opens).isEqualTo(opensBefore + 1);
        assertThat(written).isEmpty();
    }

    // -- editing -------------------------------------------------------------------------------------------

    /** The edit prompt carries the line being edited, so an anvil can pre-fill what the viewer is changing. */
    @Test
    void aShiftLeftClickOpensAPromptCarryingTheLineItWillReplace() {
        typed = null;
        property().onClick(click());

        press(11, false, true);

        assertThat(prompted).hasSize(1);
        assertThat(prompted.get(0).label()).isEqualTo("list.edit-prompt");
        assertThat(prompted.get(0).placeholders()).containsEntry("entry", "two");
    }

    @Test
    void aSubmittedEditReplacesThatLineAndLeavesTheOthers() {
        ListProperty property = property();

        property.applyEdit(click(), 1, "TWO");

        assertThat(written).containsExactly(List.of("one", "TWO", "three"));
    }

    @Test
    void aBlankEditChangesNothingAndReopensTheList() {
        ListProperty property = property();
        int opensBefore = opener.opens;

        property.applyEdit(click(), 1, " ");

        assertThat(written).isEmpty();
        assertThat(opener.opens).isEqualTo(opensBefore + 1);
    }

    @Test
    void anEditAimedPastTheEndOfTheListChangesNothing() {
        ListProperty property = property();

        property.applyEdit(click(), 9, "nine");

        assertThat(written).isEmpty();
        assertThat(value.get()).containsExactly("one", "two", "three");
    }

    // -- removing ------------------------------------------------------------------------------------------

    /** A removal is the one destructive gesture, so it asks first rather than acting on the click. */
    @Test
    void aShiftRightClickAsksBeforeItRemovesAnything() {
        property().onClick(click());

        press(11, true, true);

        assertThat(confirms.opens).isEqualTo(1);
        assertThat(written).isEmpty();
    }

    @Test
    void confirmingTheRemovalDropsThatLineAndKeepsTheRest() {
        property().onClick(click());
        press(11, true, true);

        java.util.Objects.requireNonNull(confirms.onYes).run();

        assertThat(written).containsExactly(List.of("one", "three"));
    }

    @Test
    void decliningTheRemovalReopensTheListAndKeepsEveryLine() {
        property().onClick(click());
        press(11, true, true);
        int opensBefore = opener.opens;

        java.util.Objects.requireNonNull(confirms.onNo).run();

        assertThat(written).isEmpty();
        assertThat(opener.opens).isEqualTo(opensBefore + 1);
    }

    @Test
    void confirmingARemovalOfALineThatIsAlreadyGoneWritesNothing() {
        property().onClick(click());
        press(12, true, true);
        value.set(List.of("one"));

        java.util.Objects.requireNonNull(confirms.onYes).run();

        assertThat(written).isEmpty();
        assertThat(value.get()).containsExactly("one");
    }

    // -- back, and the threads a write crosses -------------------------------------------------------------

    @Test
    void backReopensTheParentEditorRatherThanTheList() {
        property().onClick(click());
        int opensBefore = opener.opens;

        press(26, false, false);

        assertThat(reopens).isEqualTo(1);
        assertThat(opener.opens).as("the list is not reopened: the editor is").isEqualTo(opensBefore);
    }

    /** Every write goes off the tick thread and the redraw comes back onto the viewer's. */
    @Test
    void aWriteCrossesToTheAsyncThreadAndTheRedrawCrossesBack() {
        property().onClick(click());
        int asyncBefore = scheduler.asyncHops;
        int entityBefore = scheduler.entityHops;

        press(11, false, false);

        assertThat(scheduler.asyncHops).isEqualTo(asyncBefore + 1);
        assertThat(scheduler.entityHops).isGreaterThan(entityBefore);
    }

    /** The list handed to the setter is a copy, so a caller holding it cannot edit the menu's next draw. */
    @Test
    void theListTheSetterReceivesCannotBeEditedByItsCaller() {
        ListProperty property = property();

        property.applyAdd(click(), "four");

        assertThat(written).hasSize(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> written.get(0).add("five"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

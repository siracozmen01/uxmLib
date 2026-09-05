package com.uxplima.uxmlib.menu.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A property whose click is a typed line. It has no sub-menu of its own, so the only two things it decides are
 * whether the line is acceptable and, when it is, what to write: the validator's answer rather than what was typed.
 * A validator that normalises is what tells those two apart.
 */
class TextPropertyTest {

    private final SameThreadScheduler scheduler = new SameThreadScheduler();

    private final AtomicReference<String> value = new AtomicReference<>("before");

    private final List<String> written = new ArrayList<>();

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

    /** A property whose validator rejects blank lines and upper-cases everything else, so normalising is visible. */
    private TextProperty property() {
        return new TextProperty(
                "editor.name",
                "label",
                "editor.name-prompt",
                Material.PAPER,
                value::get,
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim().toUpperCase(Locale.ROOT)),
                next -> {
                    written.add(next);
                    value.set(next);
                },
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
        return new PropertyClick(
                viewer,
                false,
                false,
                () -> reopens++,
                (who, title, rows, filler, buttons) -> {
                    throw new UnsupportedOperationException("a text property opens no picker");
                },
                (who, title, onYes, onNo) -> {
                    throw new UnsupportedOperationException("a text property opens no confirm");
                });
    }

    @Test
    void theValueLineIsWhateverTheGetterReports() {
        assertThat(property().valueLore(viewer)).isEqualTo("before");
    }

    /** A text property has no picker: a click goes straight to the prompt, naming the operator's input point. */
    @Test
    void aClickOpensThePromptAtTheConfiguredInputPoint() {
        typed = null;

        property().onClick(click());

        assertThat(prompted).hasSize(1);
        assertThat(prompted.get(0).key()).isEqualTo("editor.name");
        assertThat(prompted.get(0).label()).isEqualTo("editor.name-prompt");
    }

    @Test
    void cancellingThePromptReopensTheEditorAndWritesNothing() {
        typed = null;

        property().onClick(click());

        assertThat(written).isEmpty();
        assertThat(reopens).isEqualTo(1);
    }

    @Test
    void anAcceptedLineIsWrittenAndTheEditorRedrawn() {
        typed = "after";

        property().onClick(click());

        assertThat(written).containsExactly("AFTER");
        assertThat(reopens).isEqualTo(1);
    }

    /**
     * The setter receives the validator's answer, not the typed line. A validator that only said yes or no could not
     * normalise, and this is the assertion that tells the two designs apart.
     */
    @Test
    void whatIsWrittenIsTheValidatorsAnswerAndNotWhatWasTyped() {
        property().applyInput(click(), "  padded  ");

        assertThat(written).containsExactly("PADDED");
    }

    @Test
    void aRejectedLineWritesNothingAndRedrawsTheEditor() {
        property().applyInput(click(), "   ");

        assertThat(written).isEmpty();
        assertThat(reopens).isEqualTo(1);
        assertThat(value.get()).isEqualTo("before");
    }

    /** A rejected line does not cross a thread: there is nothing to write, so there is nothing to leave the tick for. */
    @Test
    void aRejectedLineTakesNoThreadHopAtAll() {
        int asyncBefore = scheduler.asyncHops;

        property().applyInput(click(), "");

        assertThat(scheduler.asyncHops).isEqualTo(asyncBefore);
    }

    @Test
    void anAcceptedLineCrossesToTheAsyncThreadAndTheRedrawCrossesBack() {
        int asyncBefore = scheduler.asyncHops;
        int entityBefore = scheduler.entityHops;

        property().applyInput(click(), "after");

        assertThat(scheduler.asyncHops).isEqualTo(asyncBefore + 1);
        assertThat(scheduler.entityHops).isGreaterThan(entityBefore);
    }
}

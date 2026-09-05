package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The one report shared by every place that draws a code-sized list into an operator-sized set of slots. The value it
 * returns is the whole of its effect on drawing: a slot that does not exist cannot be painted either way, and what
 * this adds is that somebody is told.
 *
 * <p>Each test names its own layout, and that is not tidiness. The report is remembered per layout for the life of
 * the process, so two tests sharing a layout value would be the same report and the second would see nothing. A test
 * that has to be run first to pass is a test that will fail the week somebody adds another one.
 */
class SlotFitTest {

    private final List<String> warnings = new ArrayList<>();

    private final Logger log = Logger.getLogger(SlotFit.class.getName());

    private Handler capture;

    @BeforeEach
    void setUp() {
        capture = new Handler() {

            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        log.addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        log.removeHandler(capture);
    }

    @Test
    void aListThatFitsIsDrawnWholeAndSaysNothing() {
        assertThat(SlotFit.fit(3, 3, "editor properties", List.of(1, 2, 3))).isEqualTo(3);
        assertThat(warnings).isEmpty();
    }

    @Test
    void aListShorterThanTheSlotsIsDrawnWholeAndSaysNothing() {
        assertThat(SlotFit.fit(2, 5, "editor properties", List.of(4, 5, 6, 7, 8)))
                .isEqualTo(2);
        assertThat(warnings).isEmpty();
    }

    @Test
    void aListLongerThanTheSlotsIsCutToTheSlotsAndReported() {
        assertThat(SlotFit.fit(7, 3, "enum options", List.of(9, 10, 11))).isEqualTo(3);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("enum options")
                .contains("7")
                .contains("3")
                .contains("4");
    }

    /**
     * An editor repaints on every click and a picker reopens on every change, so a report per draw would fill a log
     * with one fact. Ten draws of the same over-full layout is one line.
     */
    @Test
    void theSameLayoutIsReportedOnceHoweverManyTimesItIsDrawn() {
        List<Integer> layout = List.of(12, 13);
        for (int draw = 0; draw < 10; draw++) {
            SlotFit.fit(5, 2, "list entries", layout);
        }

        assertThat(warnings).hasSize(1);
    }

    /**
     * Keyed by the layout's value rather than its identity, because the objects that hold a layout are rebuilt on
     * every draw. Two equal layouts are one report; the property object they came from is not the question.
     */
    @Test
    void twoEqualLayoutsAreTheSameReportEvenWhenTheyAreDifferentObjects() {
        SlotFit.fit(5, 2, "list entries", new ArrayList<>(List.of(14, 15)));
        SlotFit.fit(5, 2, "list entries", new ArrayList<>(List.of(14, 15)));

        assertThat(warnings).hasSize(1);
    }

    @Test
    void twoDifferentLayoutsAreTwoReports() {
        SlotFit.fit(5, 2, "list entries", List.of(16, 17));
        SlotFit.fit(5, 2, "list entries", List.of(18, 19));

        assertThat(warnings).hasSize(2);
    }

    /** The same layout truncating two different lists is two facts an operator needs, so it is two reports. */
    @Test
    void oneLayoutTruncatingTwoDifferentListsIsTwoReports() {
        List<Integer> layout = List.of(20, 21);
        SlotFit.fit(5, 2, "list entries", layout);
        SlotFit.fit(5, 2, "colour palette", layout);

        assertThat(warnings).hasSize(2);
    }

    /**
     * The report says how many are missing and names both fixes without choosing between them. At four of the five
     * sites the short side is the operator's layout; at the fifth the long side is a content provider's return value,
     * which is a plugin author's business. The only thing true everywhere is that there are more things than places.
     */
    @Test
    void theReportSaysHowManyAreMissingAndNamesBothFixes() {
        SlotFit.fit(9, 4, "colour palette", List.of(22, 23, 24, 25));

        assertThat(warnings.get(0))
                .contains("5 are not shown")
                .contains("longer than intended")
                .contains("needs more slots");
    }
}

package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.uxplima.uxmlib.menu.eval.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * One page of a paged list, assembled. The pinned rows claim their slots first and the flow rows take what is left,
 * which is the whole rule, and the interesting half of it is what happens when a source returns more than a page can
 * hold.
 *
 * <p>That case is a bug in the source rather than an operator's short layout, so unlike the engine's other truncation
 * sites it reports every time rather than once. A silently kept overflow would read to an operator as "the viewer saw
 * everything", which is why the warning is asserted as carefully as the trimming.
 */
class PagedListRowsTest {

    private final List<String> warnings = new ArrayList<>();

    private Logger log;

    private Handler handler;

    @BeforeEach
    void setUp() {
        log = Logger.getLogger(PagedListRowsTest.class.getName());
        log.setUseParentHandlers(false);
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (Level.WARNING.equals(record.getLevel())) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        log.addHandler(handler);
    }

    @AfterEach
    void tearDown() {
        log.removeHandler(handler);
    }

    private static PagedResult<String> page(List<String> rows, List<String> pinned) {
        return new PagedResult<>(rows, rows.size() + pinned.size(), pinned);
    }

    // -- the ordinary page ---------------------------------------------------------------------------------

    @Test
    void thePinnedRowsComeFirstAndTheFlowRowsFollowInOrder() {
        List<Object> combined =
                PagedListRows.combine("warps", 9, page(List.of("a", "b"), List.of("home", "spawn")), log);

        assertThat(combined).containsExactly("home", "spawn", "a", "b");
        assertThat(warnings).isEmpty();
    }

    @Test
    void aPageWithNothingPinnedIsJustItsFlowRows() {
        List<Object> combined = PagedListRows.combine("warps", 9, PagedResult.of(List.of("a", "b"), 2), log);

        assertThat(combined).containsExactly("a", "b");
    }

    @Test
    void anEmptyPageComesBackEmptyRatherThanNull() {
        List<Object> combined = PagedListRows.combine("warps", 9, PagedResult.of(List.of(), 0), log);

        assertThat(combined).isEmpty();
        assertThat(warnings).isEmpty();
    }

    /** A page that exactly fills its slots is not an overflow, so it must be silent. */
    @Test
    void aPageThatExactlyFillsTheSlotsSaysNothing() {
        List<Object> combined = PagedListRows.combine("warps", 3, page(List.of("a", "b"), List.of("home")), log);

        assertThat(combined).containsExactly("home", "a", "b");
        assertThat(warnings).isEmpty();
    }

    // -- the pinned rows take their slots first ------------------------------------------------------------

    /**
     * The pinned rows are not merely first, they reduce what the flow may claim. A test that only checked the order
     * would pass against an implementation that gave the flow the whole page and drew the pinned rows over it.
     */
    @Test
    void thePinnedRowsReduceWhatTheFlowRowsMayClaim() {
        List<Object> combined =
                PagedListRows.combine("warps", 4, page(List.of("a", "b", "c", "d"), List.of("home", "spawn")), log);

        assertThat(combined).containsExactly("home", "spawn", "a", "b");
    }

    // -- an over-returning source --------------------------------------------------------------------------

    @Test
    void aSourceThatReturnsMoreThanThePageHoldsIsTrimmedToWhatFits() {
        List<Object> combined = PagedListRows.combine("warps", 2, PagedResult.of(List.of("a", "b", "c", "d"), 4), log);

        assertThat(combined).containsExactly("a", "b");
    }

    /** The warning names the source and both numbers, because an operator's next question is which source and by how much. */
    @Test
    void theOverflowIsReportedWithTheSourceAndTheNumbers() {
        PagedListRows.combine("warps", 2, PagedResult.of(List.of("a", "b", "c", "d"), 4), log);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("paged_source_overflowed")
                .contains("id=warps")
                .contains("size=2")
                .contains("rows=4")
                .contains("pinned=0");
    }

    /**
     * Unlike the layout-overflow report, this one is not deduplicated. The layout an operator wrote is one fact that
     * stays true until they edit it; a source over-returning is a fresh event on every page, and an operator flipping
     * pages needs to know it is still happening.
     */
    @Test
    void theOverflowIsReportedOnEveryPageRatherThanOnceForTheSource() {
        PagedListRows.combine("warps", 2, PagedResult.of(List.of("a", "b", "c"), 3), log);
        PagedListRows.combine("warps", 2, PagedResult.of(List.of("d", "e", "f"), 3), log);

        assertThat(warnings).hasSize(2);
    }

    /**
     * Pinned rows alone can fill the page, leaving the flow nothing. They are still kept whole: dropping a pinned row
     * would hide the entry a source deliberately promoted, and the render site reports the leftover instead.
     */
    @Test
    void pinnedRowsThatFillThePageLeaveTheFlowNothingAndAreThemselvesKept() {
        List<Object> combined =
                PagedListRows.combine("warps", 2, page(List.of("a", "b"), List.of("home", "spawn")), log);

        assertThat(combined).containsExactly("home", "spawn");
        assertThat(warnings).hasSize(1);
    }

    @Test
    void pinnedRowsBeyondThePageAreNotTrimmedHere() {
        List<Object> combined = PagedListRows.combine("warps", 1, page(List.of("a"), List.of("home", "spawn")), log);

        assertThat(combined)
                .as("more than the page holds, left for the renderer to report and cut")
                .containsExactly("home", "spawn");
    }
}

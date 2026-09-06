package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The snapshot a paged list hands the renderer. Its presence is the signal that the rows are already exactly one page,
 * so what it carries has to be right at the moment it is taken: the page indicator a player reads comes from here and
 * from nowhere else. The class also claims to mirror {@link ListQueryState#pageCount(int)}, and a claim that two
 * classes agree is worth an assertion rather than a comment.
 */
class PagedListViewTest {

    @Test
    void aFullPageAndAPartialOneCountTheSame() {
        assertThat(new PagedListView(0, 45, 45).pageCount()).isEqualTo(1);
        assertThat(new PagedListView(0, 46, 45).pageCount()).isEqualTo(2);
        assertThat(new PagedListView(0, 90, 45).pageCount()).isEqualTo(2);
        assertThat(new PagedListView(0, 91, 45).pageCount()).isEqualTo(3);
    }

    @Test
    void anEmptyCorpusStillFillsOnePage() {
        // Zero pages would make the indicator read "page 1 of 0" and would make a next-page guard compare against
        // nothing. One empty page is the shape the renderer can draw.
        assertThat(new PagedListView(0, 0, 45).pageCount()).isEqualTo(1);
    }

    @Test
    void aCorpusLargerThanAnIntStillCountsWithoutOverflowing() {
        // The ceiling is computed in long arithmetic. Adding size-1 to a total near Integer.MAX_VALUE would wrap if
        // it were computed in int, and the page count would come back negative.
        assertThat(new PagedListView(0, Integer.MAX_VALUE, 45).pageCount()).isEqualTo(47721859);
        // The result is narrowed to an int, so a page count above Integer.MAX_VALUE would truncate. Reaching it
        // needs a list source that answers with more than two billion rows at a page size of one, which no menu
        // does, so this is left as a boundary rather than guarded: a guard here would be unreachable code.
    }

    @Test
    void theSnapshotAgreesWithTheLiveStateItWasTakenFrom() {
        ListQueryState state = new ListQueryState(List.of());
        for (long total : List.of(0L, 1L, 44L, 45L, 46L, 1000L)) {
            state.recordTotal(total);
            for (int size : List.of(1, 7, 45)) {
                assertThat(new PagedListView(0, total, size).pageCount())
                        .as("total %d over pages of %d", total, size)
                        .isEqualTo(state.pageCount(size));
            }
        }
    }

    @Test
    void aNegativePageOrTotalIsRefusedWhereItIsBuilt() {
        // The snapshot travels to the renderer and is read by the page indicator, so a nonsensical value has to fail
        // at the point it is assembled rather than as a wrong number in front of a player.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PagedListView(-1, 10, 45))
                .withMessageContaining("page must not be negative");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PagedListView(0, -1, 45))
                .withMessageContaining("totalCount must not be negative");
    }

    @Test
    void aPageSizeOfZeroIsRefusedRatherThanDividingByIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PagedListView(0, 10, 0))
                .withMessageContaining("page size must be positive");
        assertThatIllegalArgumentException().isThrownBy(() -> new PagedListView(0, 10, -45));
    }

    @Test
    void theSnapshotCarriesTheValuesItWasGivenAndNothingElse() {
        PagedListView view = new PagedListView(3, 100, 9);
        assertThat(view.page()).isEqualTo(3);
        assertThat(view.totalCount()).isEqualTo(100);
        assertThat(view.size()).isEqualTo(9);
    }
}

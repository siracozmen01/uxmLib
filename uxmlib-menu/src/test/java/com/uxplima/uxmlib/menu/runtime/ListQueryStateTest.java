package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmlib.menu.eval.PageRequest;
import org.junit.jupiter.api.Test;

/**
 * One viewer's browsing state for one paged list. The rule the whole class turns on is that anything changing what
 * the query returns sends the viewer back to page zero, because a page number that survived a filter would land past
 * the end of a shorter result and read to the viewer as an empty list.
 */
class ListQueryStateTest {

    private static final List<String> SORTS = List.of("name", "created", "visits");

    @Test
    void aFreshStateIsOnPageZeroWithNoFiltersAndTheFirstOfferedSort() {
        ListQueryState state = new ListQueryState(SORTS);

        assertThat(state.page()).isZero();
        assertThat(state.sort()).isEqualTo("name");
        assertThat(state.filters()).isEmpty();
        assertThat(state.total()).isZero();
    }

    @Test
    void theNextSortWrapsPastTheLastBackToTheFirst() {
        ListQueryState state = new ListQueryState(SORTS);

        state.nextSort();
        assertThat(state.sort()).isEqualTo("created");
        state.nextSort();
        assertThat(state.sort()).isEqualTo("visits");
        state.nextSort();
        assertThat(state.sort()).isEqualTo("name");
    }

    @Test
    void thePreviousSortWrapsPastTheFirstBackToTheLast() {
        ListQueryState state = new ListQueryState(SORTS);

        state.previousSort();
        assertThat(state.sort()).isEqualTo("visits");
        state.previousSort();
        assertThat(state.sort()).isEqualTo("created");
    }

    @Test
    void everySortMovementReturnsTheViewerToPageZero() {
        ListQueryState state = new ListQueryState(SORTS);

        state.page(7);
        state.nextSort();
        assertThat(state.page()).isZero();

        state.page(7);
        state.previousSort();
        assertThat(state.page()).isZero();

        state.page(7);
        state.resetSort();
        assertThat(state.page()).isZero();
    }

    @Test
    void resetSortReturnsToTheFirstOfferedSort() {
        ListQueryState state = new ListQueryState(SORTS);

        state.nextSort();
        state.nextSort();
        state.resetSort();

        assertThat(state.sort()).isEqualTo("name");
    }

    @Test
    void aSpecOfferingNoSortsReadsAsTheSourcesDefaultOrderAndCyclingDoesNothing() {
        ListQueryState state = new ListQueryState(List.of());

        state.page(3);
        state.nextSort();
        state.previousSort();

        assertThat(state.sort()).isEmpty();
        assertThat(state.page())
                .as("a no-op cycle must not move a viewer who cannot sort at all")
                .isEqualTo(3);
    }

    @Test
    void aFilterSendsTheViewerBackToPageZero() {
        ListQueryState state = new ListQueryState(SORTS);

        state.page(7);
        state.filter("search", "shop");

        assertThat(state.page()).isZero();
        assertThat(state.filters()).containsExactly(Map.entry("search", "shop"));
    }

    @Test
    void aBlankValueClearsTheFilterRatherThanMatchingOnEmptiness() {
        ListQueryState state = new ListQueryState(SORTS);

        state.filter("search", "shop");
        state.filter("search", "");

        assertThat(state.filters()).isEmpty();
    }

    @Test
    void clearingOneFilterLeavesTheOthersAndZeroesThePage() {
        ListQueryState state = new ListQueryState(SORTS);

        state.filter("search", "shop");
        state.filter("owner", "sirac");
        state.page(4);
        state.clearFilter("search");

        assertThat(state.filters()).containsExactly(Map.entry("owner", "sirac"));
        assertThat(state.page()).isZero();
    }

    @Test
    void theFilterSnapshotDoesNotSeeALaterChange() {
        ListQueryState state = new ListQueryState(SORTS);

        state.filter("search", "shop");
        Map<String, String> snapshot = state.filters();
        state.filter("search", "farm");

        assertThat(snapshot).containsExactly(Map.entry("search", "shop"));
    }

    @Test
    void theOfferedSortsAreCopiedSoTheCallersListCannotChangeThemLater() {
        List<String> mutable = new ArrayList<>(List.of("name"));
        ListQueryState state = new ListQueryState(mutable);

        mutable.add("created");
        state.nextSort();

        assertThat(state.sort()).isEqualTo("name");
    }

    @Test
    void anEmptyCorpusStillRendersOnePage() {
        ListQueryState state = new ListQueryState(SORTS);

        state.recordTotal(0);

        assertThat(state.pageCount(9)).isEqualTo(1);
    }

    @Test
    void aPartialLastPageStillCounts() {
        ListQueryState state = new ListQueryState(SORTS);

        state.recordTotal(19);

        assertThat(state.pageCount(9)).isEqualTo(3);
    }

    @Test
    void anExactlyFilledCorpusDoesNotGainATrailingEmptyPage() {
        ListQueryState state = new ListQueryState(SORTS);

        state.recordTotal(18);

        assertThat(state.pageCount(9)).isEqualTo(2);
    }

    @Test
    void theRequestCarriesThePageTheSortAndTheFilters() {
        ListQueryState state = new ListQueryState(SORTS);

        state.filter("search", "shop");
        state.page(2);
        state.nextSort();
        state.page(2);

        PageRequest request = state.request(9);

        assertThat(request.page()).isEqualTo(2);
        assertThat(request.size()).isEqualTo(9);
        assertThat(request.sort()).isEqualTo("created");
        assertThat(request.filters()).containsExactly(Map.entry("search", "shop"));
    }

    @Test
    void aNegativePageIsRejectedRatherThanClamped() {
        assertThatThrownBy(() -> new ListQueryState(SORTS).page(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativeTotalIsRejected() {
        assertThatThrownBy(() -> new ListQueryState(SORTS).recordTotal(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPageSizeOfZeroIsRejectedByBothTheCountAndTheRequest() {
        ListQueryState state = new ListQueryState(SORTS);

        assertThatThrownBy(() -> state.pageCount(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state.request(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

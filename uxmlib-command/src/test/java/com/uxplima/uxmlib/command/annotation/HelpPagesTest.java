package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Covers the pure help-pagination logic: a flat list of entries is sliced into fixed-size pages, the page
 * number is clamped into range, and the page count is computed correctly for an exact and a ragged last page.
 * Kept free of Brigadier and Adventure so it is exercised directly.
 */
class HelpPagesTest {

    private static List<HelpRenderer.Entry> entries(int count) {
        List<HelpRenderer.Entry> list = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new HelpRenderer.Entry("sub" + i, "does " + i, ""));
        }
        return list;
    }

    @Test
    void pageCountRoundsUpForARaggedLastPage() {
        assertThat(HelpPages.pageCount(13, 5)).isEqualTo(3);
        assertThat(HelpPages.pageCount(10, 5)).isEqualTo(2);
        assertThat(HelpPages.pageCount(0, 5)).isEqualTo(1);
    }

    @Test
    void slicesTheRequestedPage() {
        List<HelpRenderer.Entry> page2 = HelpPages.slice(entries(13), 2, 5);
        assertThat(page2).extracting(HelpRenderer.Entry::usage).containsExactly("sub5", "sub6", "sub7", "sub8", "sub9");
    }

    @Test
    void theLastPageMayBeShorter() {
        List<HelpRenderer.Entry> page3 = HelpPages.slice(entries(13), 3, 5);
        assertThat(page3).extracting(HelpRenderer.Entry::usage).containsExactly("sub10", "sub11", "sub12");
    }

    @Test
    void aPageBelowOneClampsToTheFirst() {
        List<HelpRenderer.Entry> page = HelpPages.slice(entries(13), 0, 5);
        assertThat(page).extracting(HelpRenderer.Entry::usage).containsExactly("sub0", "sub1", "sub2", "sub3", "sub4");
    }

    @Test
    void aPageAboveTheLastClampsToTheLast() {
        List<HelpRenderer.Entry> page = HelpPages.slice(entries(13), 99, 5);
        assertThat(page).extracting(HelpRenderer.Entry::usage).containsExactly("sub10", "sub11", "sub12");
    }

    @Test
    void clampKeepsAValidPageUnchanged() {
        assertThat(HelpPages.clamp(2, 13, 5)).isEqualTo(2);
        assertThat(HelpPages.clamp(-3, 13, 5)).isEqualTo(1);
        assertThat(HelpPages.clamp(42, 13, 5)).isEqualTo(3);
    }
}

package com.uxplima.uxmlib.command.annotation;

import java.util.List;

/**
 * The pure pagination arithmetic behind a clickable {@code /help}: how many pages a list of entries fills at
 * a given page size, the (1-based) page number clamped into range, and the slice of entries on a page. Kept
 * free of Brigadier and Adventure so the slicing and clamping are unit-tested directly; {@link HelpRenderer}
 * lays the resulting slice out as components with page-navigation buttons.
 */
final class HelpPages {

    private HelpPages() {}

    /** The number of pages {@code total} entries fill at {@code perPage}; always at least one (an empty page). */
    static int pageCount(int total, int perPage) {
        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be positive");
        }
        if (total <= 0) {
            return 1;
        }
        return (total + perPage - 1) / perPage;
    }

    /** {@code page} clamped to {@code [1, pageCount(total, perPage)]}, so an out-of-range request never fails. */
    static int clamp(int page, int total, int perPage) {
        int pages = pageCount(total, perPage);
        if (page < 1) {
            return 1;
        }
        return Math.min(page, pages);
    }

    /** The slice of {@code entries} on the (clamped 1-based) {@code page} at {@code perPage} per page. */
    static <T> List<T> slice(List<T> entries, int page, int perPage) {
        int clamped = clamp(page, entries.size(), perPage);
        int from = (clamped - 1) * perPage;
        int to = Math.min(from + perPage, entries.size());
        if (from >= to) {
            return List.of();
        }
        return List.copyOf(entries.subList(from, to));
    }
}

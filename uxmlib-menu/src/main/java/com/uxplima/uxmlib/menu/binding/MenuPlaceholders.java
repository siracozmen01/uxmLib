package com.uxplima.uxmlib.menu.binding;

import java.util.Objects;

import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.jspecify.annotations.NullMarked;

/**
 * The placeholders the engine can answer for out of its own render context, offered as one registration a host makes
 * in a line rather than as a token every plugin re-implements.
 *
 * <p>Only paging lives here so far. The engine already computes which page a menu is on and how many pages its list
 * spans, and it stamps both onto the context before a static item draws, so a "Page 2/4" indicator needs no data of
 * its own. What it does need is the same two words meaning the same thing in every menu of every plugin, and the same
 * answer to the one question the engine cannot decide for a host: the page a viewer reads is one-based, while the page
 * the engine pages with is zero-based. A plugin that writes that conversion itself writes an off-by-one it can get
 * wrong on its own.
 *
 * <p>Nothing is registered automatically. A host calls {@link #registerPaging} when it wants these tokens, so a host
 * that already owns a {@code %page%} of its own keeps it: the registry refuses a duplicate id loudly, and a library
 * that registered behind the host's back would turn that refusal into a startup crash nobody asked for.
 */
@NullMarked
public final class MenuPlaceholders {

    /** The page the viewer is reading, counted from one. */
    public static final String PAGE = "page";

    /** How many pages the menu's paged list spans, never less than one. */
    public static final String MAX_PAGE = "max_page";

    private MenuPlaceholders() {}

    /**
     * Registers {@code %page%} and {@code %max_page%} on {@code registry}, reading both from the render context the
     * renderer stamps before static items draw. A menu with no list is one page of one, so an indicator on such a menu
     * reads "1/1" rather than "1/0".
     *
     * @throws IllegalStateException when either id is already registered, which the registry treats as a wiring
     *     mistake rather than an overwrite
     */
    public static void registerPaging(PlaceholderRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(PAGE, MenuPlaceholders::page);
        registry.register(MAX_PAGE, MenuPlaceholders::maxPage);
    }

    /** The one-based page number {@code ctx} is on, as an indicator shows it. */
    public static String page(MenuContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        return String.valueOf(ctx.page() + 1);
    }

    /** The page count {@code ctx} carries, never less than one. */
    public static String maxPage(MenuContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        return String.valueOf(Math.max(1, ctx.pageCount()));
    }
}

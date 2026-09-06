package com.uxplima.uxmlib.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Map;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The two paging tokens the library offers a host, and the one question they exist to answer the same way everywhere:
 * the engine pages from zero and a viewer reads from one. Sixteen plugins writing that conversion by hand is sixteen
 * chances to write it differently, which an operator meets as a menu whose first page calls itself page zero.
 */
class MenuPlaceholdersTest {

    private PlaceholderRegistry registry;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        registry = new PlaceholderRegistry();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void thePageAViewerReadsIsOneMoreThanThePageTheEngineIsOn() {
        MenuPlaceholders.registerPaging(registry);

        assertThat(registry.resolve("page", ctx(0, 3))).contains("1");
        assertThat(registry.resolve("page", ctx(2, 3))).contains("3");
    }

    @Test
    void theCountComesFromTheContextTheRendererStamped() {
        MenuPlaceholders.registerPaging(registry);

        assertThat(registry.resolve("max_page", ctx(0, 4))).contains("4");
    }

    @Test
    void aMenuThatPagesNothingIsOnePageOfOne() {
        MenuPlaceholders.registerPaging(registry);

        assertThat(registry.resolve("page", ctx(0, 1))).contains("1");
        assertThat(registry.resolve("max_page", ctx(0, 1)))
                .as("an indicator on a menu with no list reads 1/1, never 1/0")
                .contains("1");
    }

    @Test
    void aCountThatNeverReachedTheRendererStillReadsAsOnePage() {
        assertThat(MenuPlaceholders.maxPage(MenuContext.of(viewer, null, 0).withPageCount(0)))
                .as("a context assembled by hand is not a reason to paint a zero at a player")
                .isEqualTo("1");
    }

    @Test
    void theTwoTokensAreNamedByTheirOwnConstants() {
        MenuPlaceholders.registerPaging(registry);

        assertThat(registry.has(MenuPlaceholders.PAGE)).isTrue();
        assertThat(registry.has(MenuPlaceholders.MAX_PAGE)).isTrue();
    }

    @Test
    void aHostThatAlreadyOwnsThoseTokensIsToldRatherThanOverwritten() {
        registry.register(MenuPlaceholders.PAGE, ctx -> "mine");

        assertThatIllegalStateException()
                .isThrownBy(() -> MenuPlaceholders.registerPaging(registry))
                .withMessageContaining("page");
        assertThat(registry.resolve("page", ctx(1, 2)))
                .as("the host's own token survives the refusal")
                .contains("mine");
    }

    @Test
    void nothingIsRegisteredUntilAHostAsksForIt() {
        assertThat(registry.has(MenuPlaceholders.PAGE))
                .as("a library that registers behind the host's back turns a duplicate into a startup crash")
                .isFalse();
    }

    /** A render context on {@code page} of {@code pageCount}, as the renderer stamps it before static items draw. */
    private MenuContext ctx(int page, int pageCount) {
        return MenuContext.of(viewer, null, page, Map.of()).withPageCount(pageCount);
    }
}

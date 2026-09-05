package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The per-open data every binding reads. It is immutable because one base context is reused across every slot of a list
 * page: a copy that mutated in place would leak one entry's identity into the next tile, which is a defect that shows
 * as a menu of correct-looking items all describing the same thing.
 */
class MenuContextTest {

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

    private MenuContext open() {
        return MenuContext.of(viewer, "a warp", 0, Map.of("amount", "5"));
    }

    @Test
    void aFreshOpenHasNoEntryBoundAndCountsAsOnePageUntilTheRendererKnowsBetter() {
        MenuContext ctx = open();

        assertThat(ctx.viewer()).isSameAs(viewer);
        assertThat(ctx.page()).isZero();
        assertThat(ctx.pageCount()).isEqualTo(1);
        assertThat(ctx.entry()).isEmpty();
        assertThat(ctx.localPlaceholders()).isEmpty();
        assertThat(ctx.passthrough()).isEmpty();
        assertThat(ctx.pagedViews()).isEmpty();
    }

    @Test
    void bindingAnEntryLeavesTheBaseContextUntouched() {
        MenuContext base = open();

        MenuContext bound = base.withEntry("first row");

        assertThat(base.entry())
                .as("the base is reused for every slot of the page, so one entry must not reach the next")
                .isEmpty();
        assertThat(bound.entry()).contains("first row");
    }

    @Test
    void everyCopyCarriesTheOpensViewerSubjectAndArgumentsThrough() {
        MenuContext copy = open().withEntry("row").withPage(3).withPageCount(9);

        assertThat(copy.viewer()).isSameAs(viewer);
        assertThat(copy.subject(String.class)).isEqualTo("a warp");
        assertThat(copy.arguments()).containsExactly(Map.entry("amount", "5"));
        assertThat(copy.page()).isEqualTo(3);
        assertThat(copy.pageCount()).isEqualTo(9);
        assertThat(copy.entry()).contains("row");
    }

    @Test
    void aBindingThatAsksForASubjectAMenuDoesNotHaveFailsLoudly() {
        MenuContext none = MenuContext.of(viewer, null, 0);

        assertThat(none.subjectRaw()).isEmpty();
        assertThatThrownBy(() -> none.subject(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no subject");
    }

    @Test
    void aBindingThatAsksForTheWrongSubjectTypeFailsLoudlyRatherThanReturningNull() {
        assertThatThrownBy(() -> open().subject(Integer.class))
                .as("a mismatch means a binding was wired to the wrong menu, which is worth a crash at the seam")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a Integer");
    }

    @Test
    void theEntryHasTheSameFailLoudContractAsTheSubject() {
        MenuContext ctx = open();

        assertThatThrownBy(() -> ctx.entry(String.class)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ctx.withEntry("row").entry(Integer.class)).isInstanceOf(IllegalStateException.class);
        assertThat(ctx.withEntry("row").entry(String.class)).isEqualTo("row");
    }

    @Test
    void aPassedThroughValueSurvivesEveryLaterCopy() {
        MenuContext ctx = open().withPassthrough("opener", "console")
                .withEntry("row")
                .withPage(2)
                .withPageCount(4)
                .withLocalPlaceholders(Map.of("mine", "x"))
                .withPagedViews(Map.of());

        assertThat(ctx.passthrough("opener", String.class))
                .as("a page flip or a refresh must hand a binding back exactly what the open attached")
                .isEqualTo("console");
    }

    @Test
    void askingForAValueTheOpenNeverAttachedFailsLoudly() {
        MenuContext ctx = open();

        assertThatThrownBy(() -> ctx.passthrough("opener", String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attached no value under: opener");
        assertThatThrownBy(() -> ctx.withPassthrough("opener", "console").passthrough("opener", Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a Integer");
    }

    @Test
    void attachingNothingHandsBackTheVeryContextItWasAskedOf() {
        MenuContext ctx = open();

        assertThat(ctx.withPassthrough(Map.of())).isSameAs(ctx);
    }

    @Test
    void alaterAttachReplacesAValueUnderTheSameName() {
        MenuContext ctx = open().withPassthrough("opener", "console").withPassthrough("opener", "a plugin");

        assertThat(ctx.passthrough("opener", String.class)).isEqualTo("a plugin");
    }

    @Test
    void whatAPlayerTypedAndWhatTheHostAttachedAreTwoSeparateMaps() {
        MenuContext ctx = open().withPassthrough("amount", 9000);

        assertThat(ctx.arguments())
                .as("a host value must not be claimable by a player typing its name into a command")
                .containsExactly(Map.entry("amount", "5"));
        assertThat(ctx.passthrough("amount", Integer.class)).isEqualTo(9000);
    }

    @Test
    void theAttachedMapsAreCopiedSoACallerCannotRewriteThemAfterwards() {
        Map<String, String> locals = new HashMap<>();
        locals.put("mine", "first");
        Map<String, Object> values = new HashMap<>();
        values.put("opener", "console");

        MenuContext ctx = open().withLocalPlaceholders(locals).withPassthrough(values);
        locals.put("mine", "second");
        values.put("opener", "someone else");

        assertThat(ctx.localPlaceholders()).containsExactly(Map.entry("mine", "first"));
        assertThat(ctx.passthrough("opener", String.class)).isEqualTo("console");
    }
}

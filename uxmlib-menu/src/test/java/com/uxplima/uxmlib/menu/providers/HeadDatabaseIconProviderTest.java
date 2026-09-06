package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The provider that owns the {@code hdb:} prefix and nothing else. Two properties carry it: what it claims, and what
 * it does when HeadDatabase is not there. A menu written on a server that runs HeadDatabase is copied to one that does
 * not far more often than the reverse, so the absent case is the one an operator meets.
 */
class HeadDatabaseIconProviderTest {

    /** A query that answers one known id and records every id it was asked for. */
    private static final class RecordingQuery implements HeadQuery {

        private final List<String> asked = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Optional<ItemStack> head(@Nullable String id) {
            asked.add(String.valueOf(id));
            return "1234".equals(id) ? Optional.of(new ItemStack(Material.PLAYER_HEAD)) : Optional.empty();
        }
    }

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

    private MenuContext ctx() {
        return MenuContext.of(viewer, null, 0);
    }

    @Test
    void aPrefixedIdIsResolvedThroughTheQuery() {
        RecordingQuery query = new RecordingQuery();

        assertThat(new HeadDatabaseIconProvider(query).icon("hdb:1234", ctx()))
                .map(ItemStack::getType)
                .contains(Material.PLAYER_HEAD);
        assertThat(query.asked).containsExactly("1234");
    }

    @Test
    void theIdIsHandedOverWithoutThePrefixOrTheSpaceAroundIt() {
        RecordingQuery query = new RecordingQuery();

        new HeadDatabaseIconProvider(query).icon("  hdb:  1234  ", ctx());

        assertThat(query.asked)
                .as("a query that receives the raw spec would look up a padded id and find nothing")
                .containsExactly("1234");
    }

    @Test
    void thePrefixIsReadWithoutRegardToCase() {
        RecordingQuery query = new RecordingQuery();

        assertThat(new HeadDatabaseIconProvider(query).icon("HDB:1234", ctx())).isPresent();
        assertThat(new HeadDatabaseIconProvider(query).icon("Hdb:1234", ctx())).isPresent();
    }

    @Test
    void thePrefixIsStrippedByLengthSoAnUpperCaseSpecKeepsItsOwnId() {
        RecordingQuery query = new RecordingQuery();

        new HeadDatabaseIconProvider(query).icon("HDB:AbCd", ctx());

        assertThat(query.asked)
                .as("the case-folded copy is only for the prefix test: the id must reach the query as written")
                .containsExactly("AbCd");
    }

    @Test
    void aSpecWithoutThePrefixIsNotClaimed() {
        RecordingQuery query = new RecordingQuery();
        HeadDatabaseIconProvider provider = new HeadDatabaseIconProvider(query);

        assertThat(provider.icon("PLAYER_HEAD", ctx())).isEmpty();
        assertThat(provider.icon("1234", ctx())).isEmpty();
        assertThat(provider.icon("head:1234", ctx())).isEmpty();
        assertThat(provider.icon("", ctx())).isEmpty();
        assertThat(query.asked)
                .as("an unclaimed spec must not reach the query at all")
                .isEmpty();
    }

    @Test
    void anIdTheQueryDoesNotKnowFallsThroughRatherThanFailing() {
        assertThat(new HeadDatabaseIconProvider(new RecordingQuery()).icon("hdb:nosuchhead", ctx()))
                .isEmpty();
    }

    @Test
    void withoutHeadDatabaseEveryLookupIsEmptyAndTheMenuStillRenders() {
        HeadDatabaseIconProvider provider = new HeadDatabaseIconProvider(HeadQuery.NONE);

        assertThat(provider.icon("hdb:1234", ctx())).isEmpty();
        assertThat(HeadQuery.NONE.available()).isFalse();
    }

    @Test
    void aPrefixWithNoIdBehindItAsksForNothingRatherThanForTheWholeSpec() {
        RecordingQuery query = new RecordingQuery();

        assertThat(new HeadDatabaseIconProvider(query).icon("hdb:", ctx())).isEmpty();
        assertThat(query.asked).containsExactly("");
    }
}

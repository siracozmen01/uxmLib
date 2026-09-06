package com.uxplima.uxmlib.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.util.List;

import com.uxplima.uxmlib.menu.eval.PagedResult;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

/**
 * The consumer-facing seam of the engine: a feature registers what its menus may say and do, and a spec that names
 * something nobody registered is reported before a player ever meets the menu.
 */
class MenuBindingsTest {

    private final MenuBindings bindings = new MenuBindings();

    private static MenuSpec spec(String hocon) {
        return new MenuSpecLoader().parse(hocon);
    }

    private static List<MenuSpec> one(String hocon) {
        return List.of(spec(hocon));
    }

    private static PagedResult<String> emptyPage() {
        return PagedResult.of(List.of(), 0);
    }

    @Test
    void anIdIsAnInMemorySourceOrAPagedOneAndNeverBoth() {
        bindings.list("browse", ctx -> List.of());
        assertThatThrownBy(() -> bindings.pagedList("browse", (ctx, page) -> emptyPage()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("browse");

        bindings.pagedList("people", (ctx, page) -> emptyPage());
        assertThatThrownBy(() -> bindings.list("people", ctx -> List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("people");
    }

    @Test
    void twoFeaturesClaimingOneActionIdIsALoudWiringMistake() {
        bindings.action("close", ctx -> {});
        assertThatThrownBy(() -> bindings.action("close", ctx -> {})).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFullyWiredSpecReportsNothingMissing() {
        bindings.action("warp:teleport", ctx -> {});
        bindings.condition("warp:is-server-warp", (ctx, args) -> true);
        bindings.placeholder("warp_name", ctx -> "spawn");

        assertThat(bindings.validate(one("""
                        title = "%warp_name%"
                        rows = 1
                        items { go { slot = 0, material = STONE, name = "%warp_name%",
                                     view = ["warp:is-server-warp"], click { left = ["warp:teleport"] } } }
                        """))).isEmpty();
    }

    @Test
    void anUnknownClickActionIsReportedExactlyAsItWasWritten() {
        assertThat(bindings.validate(one(
                        "rows = 1\nitems { go { slot = 0, material = STONE, click { left = [\"warp:teleprot\"] } } }")))
                .containsExactly("warp:teleprot");
    }

    @Test
    void anUnknownViewConditionIsReported() {
        assertThat(bindings.validate(
                        one("rows = 1\nitems { go { slot = 0, material = STONE, view = [\"has-permission\"] } }")))
                .containsExactly("has-permission");
    }

    @Test
    void theMenusOwnOpenAndCloseActionsAreValidatedToo() {
        assertThat(bindings.validate(one("""
                        rows = 1
                        open-requirement = [ "may-open" ]
                        open-actions = [ "announce" ]
                        close-actions = [ "forget" ]
                        items {}
                        """))).containsExactlyInAnyOrder("may-open", "announce", "forget");
    }

    @Test
    void anUnknownPlaceholderIsReportedFromTheTitleTheMaterialTheNameAndTheLore() {
        assertThat(bindings.validate(one("""
                        title = "%a%"
                        rows = 1
                        items { go { slot = 0, material = "%b%", name = "%c%", lore = ["%d%"] } }
                        """))).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    void aTokenTheMenuDeclaresInItsOwnPlaceholdersBlockCountsAsKnown() {
        assertThat(bindings.validate(one("""
                        rows = 1
                        placeholders { mine = "a literal" }
                        items { go { slot = 0, material = STONE, name = "%mine%" } }
                        """))).isEmpty();
    }

    @Test
    void aValuedTokenIsKnownWhenItsHeadIsRegistered() {
        bindings.condition("has-money", (ctx, args) -> true);
        assertThat(bindings.validate(
                        one("rows = 1\nitems { go { slot = 0, material = STONE, view = [\"has-money:100\"] } }")))
                .isEmpty();
    }

    @Test
    void aPlaceholderFallbackClaimsAWholeFamilySoNoTokenInItIsMissing() {
        bindings.placeholders().fallback(id -> id.startsWith("papi_"), (id, ctx) -> "");
        assertThat(bindings.validate(one(
                        "rows = 1\nitems { go { slot = 0, material = STONE, name = \"%papi_vault_eco_balance%\" } }")))
                .isEmpty();
    }

    @Test
    void theSameMissingIdInTwoMenusIsReportedOnce() {
        String hocon = "rows = 1\nitems { go { slot = 0, material = STONE, click { left = [\"gone\"] } } }";
        assertThat(bindings.validate(List.of(spec(hocon), spec(hocon)))).containsExactly("gone");
    }

    @Test
    void aListSourceRegisteredAsNeitherKindIsReported() {
        assertThat(bindings.validate(one("""
                        rows = 1
                        items { row { slots = ["0-8"], material = STONE,
                                      list { source = "pw:browse", template { material = PAPER } } } }
                        """)))
                .singleElement(STRING)
                .contains("pw:browse")
                .contains("neither a list nor a paged list source");
    }

    @Test
    void aPlainSourceThatSetsPagedOnlyKnobsIsReportedRatherThanSilentlyIgnored() {
        bindings.list("pw:browse", ctx -> List.of());
        assertThat(bindings.validate(one("""
                        rows = 1
                        items { row { slots = ["0-8"], material = STONE,
                                      list { source = "pw:browse", page-size = 7, sorts = ["name"],
                                             template { material = PAPER } } } }
                        """))).singleElement(STRING).contains("page-size and sorts do nothing");
    }

    @Test
    void aPagedSourceMaySetPageSizeAndSorts() {
        bindings.pagedList("pw:browse", (ctx, page) -> emptyPage());
        assertThat(bindings.validate(one("""
                        rows = 1
                        items { row { slots = ["0-8"], material = STONE,
                                      list { source = "pw:browse", page-size = 7, sorts = ["name"],
                                             template { material = PAPER } } } }
                        """))).isEmpty();
    }

    @Test
    void aSortButtonNamingAListThisMenuDoesNotContainIsReported() {
        bindings.action("list-sort", ctx -> {});
        bindings.pagedList("pw:browse", (ctx, page) -> emptyPage());
        assertThat(bindings.validate(one("""
                        rows = 6
                        items { row { slots = ["0-8"], material = STONE,
                                      list { source = "pw:browse", sorts = ["name"], template { material = PAPER } } }
                                sort { slot = 45, material = HOPPER, click { left = ["list-sort:other"] } } }
                        """)))
                .singleElement(STRING)
                .contains("names a paged list this menu does not contain");
    }

    @Test
    void aSortButtonOnASourceThatDeclaresNoSortsCannotSortSoItIsReported() {
        bindings.action("list-sort", ctx -> {});
        bindings.pagedList("pw:browse", (ctx, page) -> emptyPage());
        assertThat(bindings.validate(one("""
                        rows = 6
                        items { row { slots = ["0-8"], material = STONE,
                                      list { source = "pw:browse", template { material = PAPER } } }
                                sort { slot = 45, material = HOPPER, click { left = ["list-sort:pw:browse"] } } }
                        """))).singleElement(STRING).contains("declares no sorts");
    }

    @Test
    void aContentRegionWhoseProviderIsNotRegisteredIsReported() {
        assertThat(bindings.validate(one("rows = 3\ncontent { trade { slots = [\"0-8\"] } }\nitems {}")))
                .singleElement(STRING)
                .contains("content region 'trade' has no registered provider");
    }

    @Test
    void theSchemaOffersAPagedSourceBesideAnInMemoryOne() {
        bindings.list("warps", ctx -> List.of());
        bindings.pagedList("people", (ctx, page) -> emptyPage());

        assertThat(bindings.schema().listSourceIds())
                .as("a spec names both kinds at the same position, so a picker that offered one would hide the other")
                .containsExactly("people", "warps");
    }

    @Test
    void theSchemaIsBuiltFromTheLiveRegistriesSoAFeatureThatWiredLateIsOffered() {
        bindings.action("b", ctx -> {});
        MenuSchema before = bindings.schema();
        bindings.action("a", ctx -> {});

        assertThat(before.actionIds()).containsExactly("b");
        assertThat(bindings.schema().actionIds()).containsExactly("a", "b");
    }

    @Test
    void theAccessorsHandBackTheRegistriesTheFacadeItselfWritesTo() {
        bindings.action("open", ctx -> {});
        assertThat(bindings.actions().has("open")).isTrue();

        bindings.actions().register("late", ctx -> {});
        assertThat(bindings.action("late")).isPresent();
    }
}

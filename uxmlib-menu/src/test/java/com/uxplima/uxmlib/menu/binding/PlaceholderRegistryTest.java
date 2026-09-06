package com.uxplima.uxmlib.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The one seam a {@code %token%} resolves through. Two things about it are worth holding down: which resolver wins
 * when more than one could answer, and what happens to a resolver that cannot.
 *
 * <p>The second question has two answers in this class, and the tests say so rather than smoothing it over.
 * {@code resolveAll} catches a throwing handler and leaves its token unfilled; {@code resolve} catches nothing and
 * hands the failure to its caller, which is what lets the renderer decide for itself what a failed handler costs.
 */
class PlaceholderRegistryTest {

    private final PlaceholderRegistry registry = new PlaceholderRegistry();

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

    // -- registering ------------------------------------------------------------------------------------------

    @Test
    void aRegisteredHandlerResolvesAgainstTheOpenContext() {
        registry.register("page", ctx -> String.valueOf(ctx.page() + 1));

        assertThat(registry.resolve("page", ctx())).contains("1");
    }

    /** Two wirings claiming one token is a mistake in the plugin, not a value an operator can cause. */
    @Test
    void registeringOneIdTwiceIsRefusedRatherThanLettingTheSecondWin() {
        registry.register("page", ctx -> "first");

        assertThatIllegalStateException()
                .isThrownBy(() -> registry.register("page", ctx -> "second"))
                .withMessageContaining("page");

        assertThat(registry.resolve("page", ctx())).contains("first");
    }

    @Test
    void anIdNobodyClaimsResolvesToNothing() {
        assertThat(registry.resolve("unclaimed", ctx())).isEmpty();
    }

    // -- the family fallbacks ---------------------------------------------------------------------------------

    @Test
    void aFallbackClaimsAWholeFamilyWithoutAHandlerPerToken() {
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "expanded:" + id);

        assertThat(registry.resolve("papi_vault_balance", ctx())).contains("expanded:papi_vault_balance");
        assertThat(registry.resolve("papi_player_name", ctx())).contains("expanded:papi_player_name");
    }

    @Test
    void anExactHandlerBeatsAFallbackThatWouldAlsoClaimTheId() {
        registry.fallback(id -> true, (id, ctx) -> "fallback");
        registry.register("page", ctx -> "exact");

        assertThat(registry.resolve("page", ctx())).contains("exact");
    }

    @Test
    void whenTwoFallbacksClaimOneIdTheFirstRegisteredWins() {
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "first");
        registry.fallback(id -> id.startsWith("data_"), (id, ctx) -> "second");

        assertThat(registry.resolve("data_kills", ctx())).contains("first");
    }

    @Test
    void aFallbackThatClaimsNothingIsNotConsulted() {
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "expanded");

        assertThat(registry.resolve("page", ctx())).isEmpty();
    }

    // -- what the validator sees ------------------------------------------------------------------------------

    @Test
    void anIdAFallbackClaimsCountsAsKnownSoASpecNamingItValidates() {
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "expanded");

        assertThat(registry.has("papi_anything")).isTrue();
        assertThat(registry.has("page")).isFalse();
    }

    /**
     * A family is claimed by predicate, so it cannot be listed. The picker offers the exact tokens and leaves the
     * open-ended families to a typed one, which is why this list is deliberately shorter than what resolves.
     */
    @Test
    void theTokenListHoldsTheExactIdsSortedAndNotTheFamilies() {
        registry.register("page", ctx -> "");
        registry.register("amount", ctx -> "");
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "");

        assertThat(registry.ids()).containsExactly("amount", "page");
    }

    // -- a resolver that cannot answer ------------------------------------------------------------------------

    /** A handler may answer null, and null is not an empty string: the token resolves to nothing at all. */
    @Test
    void aHandlerAnsweringNullIsTheSameAsNoHandlerFromTheOutside() {
        registry.register("maybe", ctx -> null);

        assertThat(registry.resolve("maybe", ctx())).isEmpty();
        assertThat(registry.resolve("never-registered", ctx())).isEmpty();
    }

    @Test
    void resolveAllFillsEveryExactTokenItCan() {
        registry.register("page", ctx -> "1");
        registry.register("who", ctx -> "Sirac");

        assertThat(registry.resolveAll(ctx())).containsOnly(entry("page", "1"), entry("who", "Sirac"));
    }

    @Test
    void resolveAllDropsAHandlerThatAnswersNullRatherThanStoringNull() {
        registry.register("page", ctx -> "1");
        registry.register("maybe", ctx -> null);

        assertThat(registry.resolveAll(ctx())).containsOnlyKeys("page");
    }

    @Test
    void resolveAllSkipsAHandlerThatThrowsAndKeepsTheRest() {
        registry.register("page", ctx -> "1");
        registry.register("wrongMenu", ctx -> {
            throw new IllegalStateException("this placeholder belongs to another menu");
        });

        assertThat(registry.resolveAll(ctx())).containsOnlyKeys("page");
    }

    @Test
    void resolveAllHoldsNoFamilyToken() {
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "expanded");

        assertThat(registry.resolveAll(ctx())).isEmpty();
    }

    /**
     * The same handler, the same failure, two different outcomes depending on which seam asked. {@code resolveAll}
     * treats a throwing handler as a token it cannot fill; {@code resolve} lets the failure out and leaves the
     * decision to its caller. That is deliberate now rather than an oversight: the renderer wraps its own call, so
     * a throwing handler costs one token there, while a caller that would rather know keeps the exception.
     */
    @Test
    void resolveLetsAThrowingHandlerOutWhileResolveAllSwallowsIt() {
        registry.register("wrongMenu", ctx -> {
            throw new IllegalStateException("this placeholder belongs to another menu");
        });

        assertThat(registry.resolveAll(ctx())).isEmpty();
        assertThatThrownBy(() -> registry.resolve("wrongMenu", ctx()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another menu");
    }

    // -- the arguments themselves -----------------------------------------------------------------------------

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guards fire
    void everyEntryPointRefusesANullArgumentByName() {
        assertThatNullPointerException()
                .isThrownBy(() -> registry.register(null, ctx -> ""))
                .withMessageContaining("id");
        assertThatNullPointerException()
                .isThrownBy(() -> registry.register("page", null))
                .withMessageContaining("handler");
        assertThatNullPointerException()
                .isThrownBy(() -> registry.fallback(null, (id, ctx) -> ""))
                .withMessageContaining("claims");
        assertThatNullPointerException()
                .isThrownBy(() -> registry.resolve("page", null))
                .withMessageContaining("ctx");
    }

    private static java.util.Map.Entry<String, String> entry(String key, String value) {
        return java.util.Map.entry(key, value);
    }
}

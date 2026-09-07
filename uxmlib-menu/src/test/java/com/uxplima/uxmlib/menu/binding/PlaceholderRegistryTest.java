package com.uxplima.uxmlib.menu.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
 * <p>The second question has three answers in this class, and the tests say so rather than smoothing it over.
 * {@code resolveAll} catches a throwing handler and leaves its token unfilled; {@code resolve} catches nothing and
 * hands the failure to its caller, which is what lets the renderer decide for itself what a failed handler costs;
 * {@code resolveOrReport} catches and names it, for the caller that has already decided the token is worth less than
 * the window.
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

    // -- the third answer: resolve, but do not let a failure out ----------------------------------------------

    /**
     * A condition asks for one token by name in the middle of an operator's block, and one third-party handler that
     * throws must not take the whole gate with it and stop the menu drawing. {@code resolveOrReport} is that seam: it
     * resolves as {@code resolve} does, and a handler that throws yields nothing instead of escaping.
     */
    @Test
    void resolveOrReportYieldsNothingWhereResolveWouldThrow() {
        registry.register("wrongMenu", ctx -> {
            throw new IllegalStateException("this placeholder belongs to another menu");
        });

        assertThat(registry.resolveOrReport("wrongMenu", ctx())).isEmpty();
        assertThatThrownBy(() -> registry.resolve("wrongMenu", ctx())).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Named once per id, and per registry rather than per class. A menu that redraws every tick would otherwise
     * write a line a tick, and a static memory would let one plugin's broken handler silence another plugin's
     * report of the same token name for as long as they shared a classloader.
     */
    @Test
    void resolveOrReportNamesABrokenHandlerOnceHoweverOftenItIsAsked() {
        registry.register("wrongMenu", ctx -> {
            throw new IllegalStateException("no");
        });
        List<String> lines = captureRegistryWarnings(() -> {
            assertThat(registry.resolveOrReport("wrongMenu", ctx())).isEmpty();
            assertThat(registry.resolveOrReport("wrongMenu", ctx())).isEmpty();
            assertThat(registry.resolveOrReport("wrongMenu", ctx())).isEmpty();
        });

        assertThat(lines).singleElement().satisfies(line -> assertThat(line).contains("wrongMenu"));
    }

    @Test
    void aSecondBrokenHandlerIsNamedInItsOwnRightRatherThanSilencedByTheFirst() {
        registry.register("one", ctx -> {
            throw new IllegalStateException("no");
        });
        registry.register("two", ctx -> {
            throw new IllegalStateException("no");
        });
        List<String> lines = captureRegistryWarnings(() -> {
            registry.resolveOrReport("one", ctx());
            registry.resolveOrReport("two", ctx());
        });

        assertThat(lines).hasSize(2);
    }

    /** A fresh registry has its own memory: nothing static decides what a second one is allowed to say. */
    @Test
    void aSecondRegistryStillNamesATokenTheFirstAlreadyNamed() {
        registry.register("wrongMenu", ctx -> {
            throw new IllegalStateException("no");
        });
        PlaceholderRegistry second = new PlaceholderRegistry();
        second.register("wrongMenu", ctx -> {
            throw new IllegalStateException("no");
        });

        List<String> lines = captureRegistryWarnings(() -> {
            registry.resolveOrReport("wrongMenu", ctx());
            second.resolveOrReport("wrongMenu", ctx());
        });

        assertThat(lines).hasSize(2);
    }

    /** Away from a failure it is {@code resolve}: the exact handler, then the families, then nothing. */
    @Test
    void resolveOrReportIsOtherwiseResolve() {
        registry.register("page", ctx -> "1");
        registry.fallback(id -> id.startsWith("papi_"), (id, ctx) -> "expanded:" + id);

        assertThat(registry.resolveOrReport("page", ctx())).contains("1");
        assertThat(registry.resolveOrReport("papi_balance", ctx())).contains("expanded:papi_balance");
        assertThat(registry.resolveOrReport("unclaimed", ctx())).isEmpty();
    }

    /** The registry's own warnings raised while {@code work} runs, so "named once" is asserted rather than told. */
    private static List<String> captureRegistryWarnings(Runnable work) {
        List<String> lines = new ArrayList<>();
        Logger log = Logger.getLogger(PlaceholderRegistry.class.getName());
        Handler capture = new Handler() {

            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        log.addHandler(capture);
        try {
            work.run();
        } finally {
            log.removeHandler(capture);
        }
        return lines;
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

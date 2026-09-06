package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The six providers that reach another plugin, seen from outside. Their lookups need the plugin they integrate
 * with, so what is checked here is everything that happens before one: which spec each provider claims, and what a
 * server without any of those plugins does with a spec that names one.
 *
 * <p>The claims are checked against each other rather than one at a time. The providers are asked in turn, so a
 * prefix that another provider also answers for is not a defect in either of them: it is a defect in the pair, and
 * a test that only ever asks one provider about its own prefix cannot see it.
 */
class ReflectiveProviderPrefixesTest {

    /** Records what was said, so a silent decline can be told from a warning. */
    private static final class Recording implements Log {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            lines.add("info");
        }

        @Override
        public void warn(String message, Object... args) {
            lines.add("warn");
        }

        @Override
        public void error(String message, Throwable cause) {
            lines.add("error");
        }

        @Override
        public void debug(String message, Object... args) {
            lines.add("debug");
        }
    }

    private final Recording log = new Recording();

    private Server server;

    private PluginManager plugins;

    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Player viewer = MockBukkit.getMock().addPlayer();
        ctx = MenuContext.of(viewer, null, 0);
        server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Every reflective provider, keyed by the prefix it claims. */
    private Map<String, IconProvider> byPrefix() {
        return Map.of(
                "itemsadder:", new ItemsAdderIconProvider(server, log),
                "oraxen:", new OraxenIconProvider(server, log),
                "nexo:", new NexoIconProvider(server, log),
                "craftengine:", new CraftEngineIconProvider(server, log),
                "mmoitems:", new MMOItemsIconProvider(server, log),
                "ei:", new ExecutableItemsIconProvider(server, log));
    }

    // -- one prefix each -------------------------------------------------------------------------------------

    /**
     * No provider answers for a prefix that is not its own. The chain asks them in turn and stops at the first
     * answer, so an overlap would quietly hand an operator the wrong plugin's item, or no item at all, depending on
     * which of the two happens to be earlier in the list.
     */
    @Test
    void noProviderAnswersForAnotherProvidersPrefix() {
        Map<String, IconProvider> providers = byPrefix();
        when(plugins.isPluginEnabled(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        providers.forEach((ownPrefix, provider) -> providers.keySet().stream()
                .filter(other -> !other.equals(ownPrefix))
                .forEach(other -> assertThat(provider.icon(other + "anything", ctx))
                        .as(ownPrefix + " asked about " + other)
                        .isEmpty()));
    }

    /** A bare material name belongs to the renderer's fallback, and none of these may take it. */
    @Test
    void noProviderClaimsABareMaterialName() {
        byPrefix().forEach((prefix, provider) -> assertThat(provider.icon("DIAMOND_SWORD", ctx))
                .as(prefix)
                .isEmpty());
    }

    // -- a server that has none of them ----------------------------------------------------------------------

    /**
     * The ordinary server. Every one of these plugins is optional, so a spec naming an absent one is not a fault:
     * it resolves to nothing, the renderer draws the material fallback, and the log stays quiet. A warning here
     * would appear on every server that does not happen to run all six.
     */
    @Test
    void aServerWithNoneOfThePluginsDeclinesQuietly() {
        byPrefix().forEach((prefix, provider) -> assertThat(provider.icon(prefix + "anything", ctx))
                .as(prefix)
                .isEmpty());

        assertThat(log.lines).isEmpty();
    }

    // -- the one shape check that happens before the plugin --------------------------------------------------

    /**
     * MMOItems is keyed by a type and an id, so the value after its prefix is itself two halves. A value missing
     * either half is refused on its shape, before any class is named, which is why it costs nothing and says
     * nothing on a server that does have MMOItems.
     */
    @Test
    void anMmoItemsSpecMissingEitherHalfIsRefusedOnItsShape() {
        when(plugins.isPluginEnabled("MMOItems")).thenReturn(true);
        IconProvider provider = new MMOItemsIconProvider(server, log);

        for (String malformed : new String[] {"SWORD", ":ID", "SWORD:", "  :ID", "SWORD:  "}) {
            assertThat(provider.icon("mmoitems:" + malformed, ctx))
                    .as(malformed)
                    .isEmpty();
        }

        assertThat(log.lines).as("a shape refusal is not a failure").isEmpty();
    }

    /**
     * The contrast that makes the previous test mean something: a well-formed value does reach for the SDK, and on
     * a server without MMOItems that reach fails and is reported once. Without this, a provider that refused
     * everything would pass the shape test too.
     */
    @Test
    void aWellFormedMmoItemsSpecReachesForTheSdkAndReportsWhenItIsNotThere() {
        when(plugins.isPluginEnabled("MMOItems")).thenReturn(true);
        IconProvider provider = new MMOItemsIconProvider(server, log);

        assertThat(provider.icon("mmoitems:SWORD:CUTLASS", ctx)).isEmpty();

        assertThat(log.lines).containsExactly("warn");
    }
}

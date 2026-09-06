package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The contract five plugin integrations inherit. None of them is testable against the plugin it integrates with, so
 * everything that keeps a missing or changed third-party plugin from breaking a menu is here, in the one class that
 * has no third-party plugin behind it.
 *
 * <p>Two of these are load-bearing beyond the menu that failed. The first is that a spec this provider does not own
 * is left untouched, because the providers are asked in turn and one that answers for a prefix that is not its own
 * takes the icon away from the provider whose prefix it is. The second is that nothing reflective runs before the
 * present-guard: a server without the plugin must load none of its classes, and a lookup that ran anyway would load
 * them by name.
 */
class ReflectiveItemProviderTest {

    /** A lookup a test can point wherever it likes, including at a failure. */
    @FunctionalInterface
    private interface Lookup {
        @Nullable ItemStack apply(String id) throws ReflectiveOperationException;
    }

    /** Records what it was asked for, so "never reached" is an assertion rather than an absence of an assertion. */
    private static final class Probe extends ReflectiveItemProvider {

        private final List<String> asked = new ArrayList<>();

        private Lookup lookup = id -> null;

        Probe(Server server, Log log) {
            super("TestPlugin", "probe:", server, log);
        }

        @Override
        protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
            asked.add(id);
            return lookup.apply(id);
        }
    }

    /** Records what was said, so "warned once" and "said nothing" are both assertable. */
    private static final class Recording implements Log {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            lines.add("info");
        }

        @Override
        public void warn(String message, Object... args) {
            lines.add("warn " + message + " " + List.of(args));
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

    private Probe probe;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Player viewer = MockBukkit.getMock().addPlayer();
        ctx = MenuContext.of(viewer, null, 0);
        server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.isPluginEnabled("TestPlugin")).thenReturn(true);
        probe = new Probe(server, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack ruby() {
        return new ItemStack(Material.DIAMOND);
    }

    // -- whose spec is it ------------------------------------------------------------------------------------

    /**
     * A spec with somebody else's prefix is left for them. The providers are asked in turn, so a provider that
     * answered here would take the icon away from the provider the operator meant.
     */
    @Test
    void aSpecWithAnotherProvidersPrefixIsLeftAlone() {
        probe.lookup = id -> ruby();

        assertThat(probe.icon("oraxen:ruby", ctx)).isEmpty();
        assertThat(probe.asked).isEmpty();
        assertThat(log.lines).isEmpty();
    }

    /** A plain material name belongs to the renderer's fallback, not to any provider. */
    @Test
    void aPlainMaterialNameIsNotClaimed() {
        probe.lookup = id -> ruby();

        assertThat(probe.icon("DIAMOND", ctx)).isEmpty();
        assertThat(probe.asked).isEmpty();
    }

    /**
     * An operator writes a config by hand, so the match ignores case on both sides and the spaces around it. What
     * survives is the id, trimmed, in the case the operator wrote it: the integrated plugin decides whether its own
     * ids are case sensitive.
     */
    @Test
    void theMatchIgnoresCaseAndSurroundingSpaceAndTheIdKeepsItsOwn() {
        probe.lookup = id -> ruby();

        assertThat(probe.icon("  PROBE:  Ruby_Sword  ", ctx)).isPresent();
        assertThat(probe.asked).containsExactly("Ruby_Sword");
    }

    // -- the present guard -----------------------------------------------------------------------------------

    /**
     * Nothing reflective runs on a server without the plugin. The lookup is where a class name is first written
     * down, so reaching it at all is what would load the absent plugin's classes.
     */
    @Test
    void aServerWithoutThePluginNeverReachesTheLookup() {
        when(plugins.isPluginEnabled("TestPlugin")).thenReturn(false);
        probe.lookup = id -> ruby();

        assertThat(probe.icon("probe:ruby", ctx)).isEmpty();
        assertThat(probe.asked).isEmpty();
        assertThat(log.lines)
                .as("an absent plugin is an ordinary server, not a fault")
                .isEmpty();
    }

    /**
     * The guard is asked on every icon rather than once at construction, so a provider built while the plugin was
     * still loading starts answering as soon as it is enabled.
     */
    @Test
    void theGuardIsAskedAgainForEveryIcon() {
        when(plugins.isPluginEnabled("TestPlugin")).thenReturn(false);
        probe.lookup = id -> ruby();
        assertThat(probe.icon("probe:ruby", ctx)).isEmpty();

        when(plugins.isPluginEnabled("TestPlugin")).thenReturn(true);

        assertThat(probe.icon("probe:ruby", ctx)).isPresent();
    }

    /** A prefix with nothing after it is a config typo, and the menu shows the fallback rather than a warning. */
    @Test
    void aPrefixWithNoIdAfterItIsASilentMiss() {
        probe.lookup = id -> ruby();

        assertThat(probe.icon("probe:", ctx)).isEmpty();
        assertThat(probe.icon("probe:   ", ctx)).isEmpty();
        assertThat(probe.asked).isEmpty();
        assertThat(log.lines).isEmpty();
        verify(plugins, never()).isPluginEnabled("TestPlugin");
    }

    // -- what the lookup comes back with -----------------------------------------------------------------------

    @Test
    void aResolvedIdComesBackAsItsStack() {
        ItemStack found = ruby();
        probe.lookup = id -> found;

        assertThat(probe.icon("probe:ruby", ctx)).contains(found);
        assertThat(log.lines).isEmpty();
    }

    /** Null is the integrated plugin's own way of saying it has no such item, and it is not a failure. */
    @Test
    void anIdThePluginDoesNotKnowIsEmptyAndNotAWarning() {
        probe.lookup = id -> null;

        assertThat(probe.icon("probe:ruby", ctx)).isEmpty();
        assertThat(probe.asked).containsExactly("ruby");
        assertThat(log.lines).isEmpty();
    }

    // -- what a version bump does ------------------------------------------------------------------------------

    /**
     * A plugin update can move the API this provider reaches for. It surfaces either as a reflective miss or as an
     * unchecked failure from inside the call, and both have to end in the material fallback: a menu that throws
     * here takes the whole window down over one icon.
     */
    @Test
    void aReflectiveFailureDegradesToTheFallbackAndSaysWhichPlugin() {
        probe.lookup = id -> {
            throw new NoSuchMethodException("getCustomStack");
        };

        assertThat(probe.icon("probe:ruby", ctx)).isEmpty();
        assertThat(log.lines).singleElement().asString().startsWith("warn ").contains("TestPlugin");
    }

    @Test
    void anUncheckedFailureFromInsideTheCallDegradesTheSameWay() {
        probe.lookup = id -> {
            throw new IllegalStateException("the SDK is half loaded");
        };

        assertThat(probe.icon("probe:ruby", ctx)).isEmpty();
        assertThat(log.lines).hasSize(1);
    }

    /**
     * The warning is once for the life of the provider. Every icon of every draw of every menu would otherwise
     * take the same failing path, and a menu that refreshes on a timer would write the same line until the disk
     * filled.
     */
    @Test
    void theWarningIsSaidOnceAndNotOncePerIcon() {
        probe.lookup = id -> {
            throw new ClassNotFoundException("com.example.SdkThatMoved");
        };

        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(probe.icon("probe:ruby", ctx)).isEmpty();
        }

        assertThat(log.lines).hasSize(1);
    }
}

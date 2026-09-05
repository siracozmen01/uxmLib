package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The ordered chain the renderer consults before reading a material spec as a material name. Two properties carry the
 * whole design: a provider claims only its own prefix, so a bare material name always survives to the fallback, and a
 * runtime provider is consulted last, so a plugin can add a prefix and can never take one of ours.
 */
class IconProvidersTest {

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

    /** A provider that claims one prefix and answers with one material, so which provider answered is readable. */
    private static IconProvider claiming(String prefix, Material answer) {
        return (spec, ctx) -> spec.startsWith(prefix) ? Optional.of(new ItemStack(answer)) : Optional.empty();
    }

    @Test
    void theFirstProviderThatClaimsTheSpecAnswersAndTheRestAreNotAsked() {
        IconProviders chain =
                new IconProviders(List.of(claiming("gem:", Material.DIAMOND), claiming("gem:", Material.EMERALD)));

        assertThat(chain.resolve("gem:one", ctx())).map(ItemStack::getType).contains(Material.DIAMOND);
    }

    @Test
    void aSpecNoProviderClaimsIsLeftForTheMaterialFallback() {
        IconProviders chain = new IconProviders(List.of(claiming("gem:", Material.DIAMOND)));

        assertThat(chain.resolve("DIAMOND", ctx()))
                .as("an empty result is what tells the renderer to read the spec as a material name")
                .isEmpty();
    }

    @Test
    void theDefaultChainNeverClaimsABareMaterialName() {
        IconProviders defaults = IconProviders.defaults();

        assertThat(defaults.resolve("DIAMOND", ctx())).isEmpty();
        assertThat(defaults.resolve("PLAYER_HEAD", ctx()))
                .as("PLAYER_HEAD is the material the skull provider builds, so it is the one most at risk of capture")
                .isEmpty();
        assertThat(defaults.resolve("STONE", ctx())).isEmpty();
    }

    @Test
    void aRuntimeProviderIsAskedOnlyAfterEveryBuiltInHasDeclined() {
        IconProviderRegistry runtime = new IconProviderRegistry();
        runtime.register(claiming("gem:", Material.EMERALD));
        IconProviders chain = new IconProviders(List.of(claiming("gem:", Material.DIAMOND))).withRuntime(runtime);

        assertThat(chain.resolve("gem:one", ctx()))
                .as("a registered provider adds prefixes and can never shadow one of ours")
                .map(ItemStack::getType)
                .contains(Material.DIAMOND);
    }

    @Test
    void aRuntimeProviderClaimsAPrefixNoBuiltInOwns() {
        IconProviderRegistry runtime = new IconProviderRegistry();
        runtime.register(claiming("mine:", Material.EMERALD));
        IconProviders chain = new IconProviders(List.of(claiming("gem:", Material.DIAMOND))).withRuntime(runtime);

        assertThat(chain.resolve("mine:one", ctx())).map(ItemStack::getType).contains(Material.EMERALD);
    }

    @Test
    void aProviderRegisteredAfterTheChainWasBuiltIsSeenAtTheNextResolve() {
        IconProviderRegistry runtime = new IconProviderRegistry();
        IconProviders chain = IconProviders.defaults().withRuntime(runtime);

        assertThat(chain.resolve("mine:one", ctx())).isEmpty();
        runtime.register(claiming("mine:", Material.EMERALD));

        assertThat(chain.resolve("mine:one", ctx()))
                .as("the chain holds the registry by reference, so a plugin enabling later needs no rebuild")
                .map(ItemStack::getType)
                .contains(Material.EMERALD);
    }

    @Test
    void optingIntoARuntimeTailLeavesTheChainItWasBuiltFromAlone() {
        IconProviderRegistry runtime = new IconProviderRegistry();
        runtime.register(claiming("mine:", Material.EMERALD));
        IconProviders builtIn = IconProviders.defaults();

        IconProviders withTail = builtIn.withRuntime(runtime);

        assertThat(builtIn.resolve("mine:one", ctx()))
                .as("withRuntime returns a copy, so the built-in chain stays the pure one")
                .isEmpty();
        assertThat(withTail.resolve("mine:one", ctx())).isPresent();
    }

    @Test
    void theChainIsCopiedSoTheListItWasBuiltFromCannotChangeItLater() {
        List<IconProvider> mutable = new java.util.ArrayList<>();
        mutable.add(claiming("gem:", Material.DIAMOND));
        IconProviders chain = new IconProviders(mutable);

        mutable.add(claiming("mine:", Material.EMERALD));

        assertThat(chain.resolve("mine:one", ctx())).isEmpty();
    }

    @Test
    void aHeadDatabaseLessServerResolvesAnHdbSpecToTheMaterialFallback() {
        IconProviders chain = IconProviders.withHeadDatabase(HeadQuery.NONE);

        assertThat(HeadQuery.NONE.available()).isFalse();
        assertThat(chain.resolve("hdb:1234", ctx()))
                .as("a menu naming an HDB head must still render where HeadDatabase is not installed")
                .isEmpty();
    }
}

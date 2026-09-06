package com.uxplima.uxmlib.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The two structural choices the CraftEngine provider makes. Everything else in that class is a reflective call that
 * needs CraftEngine on the server, but these two hops are chosen by the shape of whatever object they are handed, so
 * a plain class written here stands in for a CraftEngine item exactly as far as the rule can tell.
 *
 * <p>They are worth pinning because they are what carries a CraftEngine release that renames a method: the rule says
 * which method, and a rule that quietly matches one method too many or one too few turns every custom icon on the
 * server into a material name.
 */
class CraftEngineIconProviderTest {

    /** Stands in for CraftEngine's own player type. Only its simple name reaches the rule. */
    public static final class Player {}

    /** A definition that offers the viewer-less build among methods that must not be taken for it. */
    public static final class Definition {

        public Object buildItem() {
            return "no viewer";
        }

        public Object buildItem(String other) {
            return other;
        }

        public Object buildItem(Player viewer) {
            return new Wrapper(new ItemStack(Material.DIAMOND));
        }
    }

    /** The older CraftEngine shape: another name, Bukkit's own player type, and the stack handed back directly. */
    public static final class OlderDefinition {

        public ItemStack buildItemStack(org.bukkit.entity.Player viewer) {
            return new ItemStack(Material.EMERALD);
        }
    }

    /** A definition with nothing the rule may take: a build that takes no viewer, and a viewer method that builds nothing. */
    public static final class UnknownDefinition {

        public Object buildItem() {
            return "no viewer";
        }

        public Object render(Player viewer) {
            return viewer;
        }
    }

    /** CraftEngine's item wrapper: the stack is reached through a no-argument accessor, whatever it is called. */
    public static final class Wrapper {

        private final ItemStack stack;

        Wrapper(ItemStack stack) {
            this.stack = stack;
        }

        public String name() {
            return "topaz";
        }

        public ItemStack load() {
            return stack;
        }
    }

    /** A wrapper that hands back nothing: CraftEngine's own way of saying the definition built no item. */
    public static final class EmptyWrapper {

        public @Nullable ItemStack load() {
            return null;
        }
    }

    /** A wrapper whose only stack accessor wants an argument, so the rule may not use it. */
    public static final class DemandingWrapper {

        public ItemStack stack(int amount) {
            return new ItemStack(Material.DIAMOND, amount);
        }

        public String name() {
            return "topaz";
        }
    }

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -- which method builds the item ------------------------------------------------------------------------

    /**
     * The overload that takes a viewer is the one wanted, and the two beside it are the reason the rule counts
     * parameters and reads their type rather than matching the name alone.
     */
    @Test
    void theBuildOverloadThatTakesAViewerIsTheOneChosen() throws ReflectiveOperationException {
        Method chosen = CraftEngineIconProvider.build(new Definition());

        assertThat(chosen.getName()).isEqualTo("buildItem");
        assertThat(chosen.getParameterTypes()).containsExactly(Player.class);
    }

    /**
     * The rule reads the start of the name, not the whole of it, which is what carries the release where the same
     * call was named {@code buildItemStack}.
     */
    @Test
    void theOlderNameIsTakenTooBecauseTheRuleReadsTheStartOfIt() throws ReflectiveOperationException {
        Method chosen = CraftEngineIconProvider.build(new OlderDefinition());

        assertThat(chosen.getName()).isEqualTo("buildItemStack");
    }

    /**
     * The viewer type is matched by its simple name, so Bukkit's own {@code Player} satisfies it as well as
     * CraftEngine's. That is deliberate: the older shape takes the Bukkit type, and no CraftEngine type may be named
     * here to tell the two apart.
     */
    @Test
    void theViewerTypeIsMatchedByItsNameSoBukkitsOwnPlayerSatisfiesIt() throws ReflectiveOperationException {
        Method chosen = CraftEngineIconProvider.build(new OlderDefinition());

        assertThat(chosen.getParameterTypes()).containsExactly(org.bukkit.entity.Player.class);
    }

    /**
     * A definition the rule finds nothing in is a failure with a name in it. The provider degrades that to the
     * material fallback, and the line an operator reads has to say which class had no build.
     */
    @Test
    void aDefinitionWithNoViewerLessBuildFailsAndNamesItself() {
        assertThatThrownBy(() -> CraftEngineIconProvider.build(new UnknownDefinition()))
                .isInstanceOf(NoSuchMethodException.class)
                .hasMessageContaining("UnknownDefinition");
    }

    // -- how the stack comes out of what was built -----------------------------------------------------------

    /** The older shape hands the stack back itself, and unwrapping it is doing nothing to it. */
    @Test
    void aValueThatIsAlreadyAStackIsItsOwnUnwrapping() throws ReflectiveOperationException {
        ItemStack built = new ItemStack(Material.EMERALD);

        assertThat(CraftEngineIconProvider.unwrap(built)).isSameAs(built);
    }

    /** The current shape hands back a wrapper, and the stack is whichever no-argument accessor answers with one. */
    @Test
    void theStackInsideTheWrapperIsReachedThroughItsNoArgumentAccessor() throws ReflectiveOperationException {
        ItemStack stack = new ItemStack(Material.DIAMOND);

        assertThat(CraftEngineIconProvider.unwrap(new Wrapper(stack))).isSameAs(stack);
    }

    /**
     * A wrapper that holds nothing is not a failure: CraftEngine says an item was not built by handing back nothing,
     * and the menu draws the material fallback rather than warning about a version.
     */
    @Test
    void aWrapperThatHoldsNothingIsNoItemRatherThanAFailure() throws ReflectiveOperationException {
        assertThat(CraftEngineIconProvider.unwrap(new EmptyWrapper())).isNull();
    }

    /**
     * An accessor that wants an argument is not one the rule can call, because there is no argument to give it. A
     * rule that only read return types would call it with nothing and fail inside the reflection instead.
     */
    @Test
    void anAccessorThatWantsAnArgumentIsNotTheOneUsed() {
        assertThatThrownBy(() -> CraftEngineIconProvider.unwrap(new DemandingWrapper()))
                .isInstanceOf(NoSuchMethodException.class)
                .hasMessageContaining("DemandingWrapper");
    }

    /** A definition is not a wrapper: it has no stack accessor, so unwrapping one is the same failure. */
    @Test
    void aValueWithNoStackAccessorAtAllFailsAndNamesItself() {
        assertThatThrownBy(() -> CraftEngineIconProvider.unwrap(new UnknownDefinition()))
                .isInstanceOf(NoSuchMethodException.class)
                .hasMessageContaining("UnknownDefinition");
    }

    // -- the two hops together -------------------------------------------------------------------------------

    /**
     * The pair, walked as the provider walks it. Either shape ends in the same stack, which is why the release that
     * renamed the call and changed what it returns needed no second code path.
     */
    @Test
    void bothCraftEngineShapesEndInTheSameStack() throws ReflectiveOperationException {
        Object current = CraftEngineIconProvider.build(new Definition()).invoke(new Definition(), new Object[] {null});
        Object older =
                CraftEngineIconProvider.build(new OlderDefinition()).invoke(new OlderDefinition(), new Object[] {null});

        assertThat(CraftEngineIconProvider.unwrap(current)).isEqualTo(new ItemStack(Material.DIAMOND));
        assertThat(CraftEngineIconProvider.unwrap(older)).isEqualTo(new ItemStack(Material.EMERALD));
    }
}

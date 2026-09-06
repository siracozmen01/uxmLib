package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.spec.ClickKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * What a consumer's action binding is handed on a click. It is the widest developer-API surface in the engine: every
 * plugin that registers a single action reads this object, and almost nothing else about the engine's internals.
 *
 * <p>Two properties earn their tests. The typed accessors fail loudly rather than handing back null, because an action
 * asking for the wrong type is a wiring mistake that would otherwise surface as a null dereference somewhere else
 * entirely. And a context built outside a live click carries a control that does nothing, which is what lets a plugin
 * invoke its own binding directly without the engine.
 */
class MenuActionContextTest {

    private record Warp(String name) {}

    private record Entry(int index) {}

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

    private MenuContext context() {
        return MenuContext.of(viewer, new Warp("spawn"), 2);
    }

    private MenuActionContext action(Map<String, String> args) {
        return new MenuActionContext(context(), viewer, ClickKind.LEFT, args);
    }

    // -- what it delegates to the open context ------------------------------------------------------------

    @Test
    void theViewerAndPageAreReadOffTheOpenContext() {
        MenuActionContext ctx = action(Map.of());

        assertThat(ctx.viewer()).isEqualTo(viewer);
        assertThat(ctx.page()).isEqualTo(2);
    }

    @Test
    void theWholeOpenContextIsReachableForABindingThatWantsIt() {
        MenuActionContext ctx = action(Map.of());

        assertThat(ctx.context().page()).isEqualTo(2);
    }

    @Test
    void theClickKindIsTheGestureThatFiredAndNotTheOnesThatDidNot() {
        MenuActionContext ctx = new MenuActionContext(context(), viewer, ClickKind.SHIFT_RIGHT, Map.of());

        assertThat(ctx.clickKind()).isEqualTo(ClickKind.SHIFT_RIGHT);
    }

    // -- the typed accessors ------------------------------------------------------------------------------

    @Test
    void theSubjectComesBackAsTheTypeTheBindingAsksFor() {
        assertThat(action(Map.of()).subject(Warp.class).name()).isEqualTo("spawn");
    }

    /**
     * Asking for the wrong type fails here rather than handing back null. An action that received null would fail
     * somewhere else entirely, and the wiring mistake would be read as a bug in whatever dereferenced it.
     */
    @Test
    void askingForTheWrongSubjectTypeFailsLoudly() {
        assertThatIllegalStateException()
                .isThrownBy(() -> action(Map.of()).subject(Entry.class))
                .withMessageContaining("not a Entry");
    }

    @Test
    void aMenuWithNoSubjectSaysSoRatherThanAnsweringNull() {
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(viewer, null, 0), viewer, ClickKind.LEFT, Map.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> ctx.subject(Warp.class))
                .withMessageContaining("no subject");
    }

    @Test
    void theEntryComesBackAsTheTypeTheBindingAsksFor() {
        MenuActionContext ctx =
                new MenuActionContext(context().withEntry(new Entry(7)), viewer, ClickKind.LEFT, Map.of());

        assertThat(ctx.entry(Entry.class).index()).isEqualTo(7);
    }

    @Test
    void aClickOnASlotThatIsNotAListEntrySaysSoRatherThanAnsweringNull() {
        assertThatIllegalStateException()
                .isThrownBy(() -> action(Map.of()).entry(Entry.class))
                .withMessageContaining("no entry");
    }

    // -- the ref's own arguments --------------------------------------------------------------------------

    /** The single positional argument of an {@code id:value} ref, which is what nearly every action reads. */
    @Test
    void thePositionalArgumentOfARefIsReadAsValue() {
        assertThat(action(Map.of("value", "spawn")).arg()).isEqualTo("spawn");
    }

    /** A ref that carried no argument reads as empty rather than null, so a binding can branch without a check. */
    @Test
    void aRefWithNoArgumentReadsAsEmptyAndNotAsNull() {
        assertThat(action(Map.of()).arg()).isEmpty();
    }

    @Test
    void theFullArgumentMapIsReachableForARefThatCarriesMoreThanOne() {
        assertThat(action(Map.of("value", "spawn", "world", "hub")).args())
                .containsEntry("value", "spawn")
                .containsEntry("world", "hub");
    }

    /** The map is copied at construction, so a caller that keeps its own cannot change what a binding later reads. */
    @Test
    void theArgumentsAreCopiedSoALaterEditByTheCallerDoesNotReachTheBinding() {
        Map<String, String> mutable = new HashMap<>(Map.of("value", "spawn"));
        MenuActionContext ctx = action(mutable);

        mutable.put("value", "elsewhere");

        assertThat(ctx.arg()).isEqualTo("spawn");
    }

    @Test
    void theArgumentMapHandedToABindingCannotBeEditedByIt() {
        MenuActionContext ctx = action(Map.of("value", "spawn"));

        assertThatThrownBy(() -> ctx.args().put("value", "elsewhere"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -- the control handle -------------------------------------------------------------------------------

    /**
     * A context built outside a live click carries a control bound to no window, so a plugin can invoke its own
     * binding directly. Every operation must be a no-op rather than a null dereference, including the ones that take
     * arguments, so the assertion drives all six rather than the one a refresh action happens to call.
     */
    @Test
    void aContextBuiltOutsideALiveClickCarriesAControlThatDoesNothing() {
        MenuControl control = action(Map.of()).control();

        assertThat(control).isSameAs(MenuControl.NOOP);
        control.refresh();
        control.refreshSlot(4);
        control.resetPagination();
        control.sortList("warps", com.uxplima.uxmlib.menu.spec.ListControlSyntax.SortDirection.NEXT);
        control.filterList("warps", "world", "hub");
        control.searchList("warps", "name");
    }

    @Test
    void aContextBuiltByTheEngineCarriesTheControlItWasGiven() {
        MenuControl own = MenuControl.NOOP;
        MenuActionContext ctx = new MenuActionContext(context(), viewer, ClickKind.LEFT, Map.of(), own);

        assertThat(ctx.control()).isSameAs(own);
    }

    // -- what it refuses to be built without --------------------------------------------------------------

    @SuppressWarnings("NullAway") // intentionally passes null to assert each requireNonNull guard fires
    @Test
    void everyCollaboratorIsRequiredAndTheFailureNamesTheMissingOne() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MenuActionContext(null, viewer, ClickKind.LEFT, Map.of()))
                .withMessageContaining("ctx");
        assertThatNullPointerException()
                .isThrownBy(() -> new MenuActionContext(context(), null, ClickKind.LEFT, Map.of()))
                .withMessageContaining("player");
        assertThatNullPointerException()
                .isThrownBy(() -> new MenuActionContext(context(), viewer, null, Map.of()))
                .withMessageContaining("clickKind");
        assertThatNullPointerException()
                .isThrownBy(() -> new MenuActionContext(context(), viewer, ClickKind.LEFT, null))
                .withMessageContaining("args");
        assertThatNullPointerException()
                .isThrownBy(() -> new MenuActionContext(context(), viewer, ClickKind.LEFT, Map.of(), null))
                .withMessageContaining("control");
    }
}

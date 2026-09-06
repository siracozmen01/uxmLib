package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.style.MenuTitles;
import com.uxplima.uxmlib.menu.EditorSpec;
import com.uxplima.uxmlib.menu.EntityEditorLayout;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The one re-render an editor property's reopen hook runs. It has two ways to repaint and the choice between them is
 * the whole of it: repaint the live window when the viewer is still looking at it, and build a fresh one when the
 * viewer is not. Getting that choice wrong is not visible as an exception; it is visible as a window that stops
 * responding, or as a second window stacked on the first, so both branches are asserted on what the viewer ends up
 * looking at rather than on what was called.
 *
 * <p>The holder is the lineage. Both branches keep it, which is what lets the one listener and the one teardown go on
 * owning a window that was repainted by either route.
 */
class EditorRefreshTest {

    /** A catalogue that hands every key straight back, so a rendered line is readable in an assertion. */
    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text(key);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** A property that reports a fixed label and whatever the subject currently says, so a repaint is observable. */
    private record Fixed(String label, String value) implements EditableProperty {

        @Override
        public Material icon() {
            return Material.DIAMOND;
        }

        @Override
        public String valueLore(Player viewer) {
            return value;
        }

        @Override
        public void onClick(PropertyClick click) {}
    }

    /** Records the hop rather than taking it, so the work can be inspected before and after it runs. */
    private static final class Deferring extends SameThreadScheduler {

        private final List<Runnable> queued = new ArrayList<>();

        private final List<Entity> hoppedTo = new ArrayList<>();

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            hoppedTo.add(entity);
            queued.add(task);
            return FINISHED;
        }

        void runQueued() {
            List<Runnable> due = List.copyOf(queued);
            queued.clear();
            due.forEach(Runnable::run);
        }
    }

    private PlayerMock viewer;

    private final List<String> values = new ArrayList<>(List.of("hard"));

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static EditorRenderer renderer() {
        return new EditorRenderer(new PlainText(), Theme::defaults);
    }

    private EditorSpec spec() {
        return EditorSpec.builder()
                .layout(EntityEditorLayout.codeDefault(List.of(10, 12), 22))
                .title((who, subject) -> Component.text("editing " + subject))
                .valueLore("value: %value%")
                .backName("back")
                .properties(subject -> List.of(new Fixed("difficulty", values.get(0))))
                .onBack(who -> {})
                .build();
    }

    /** A holder carrying an open editor window, the state the reopen hook finds it in. */
    private MenuHolder openEditor(EditorSpec spec) {
        MenuHolder holder =
                new MenuHolder("editor", new MenuSpecLoader().parse("rows = 3"), MenuContext.of(viewer, "home", 0));
        EditorState state = new EditorState(spec, "home");
        holder.attachEditor(state);
        Inventory inv =
                MockBukkit.getMock().createInventory(holder, spec.layout().rows() * 9);
        holder.attach(inv);
        renderer().populate(inv, spec, state, viewer);
        viewer.openInventory(inv);
        return holder;
    }

    // -- the hop ---------------------------------------------------------------------------------------------

    /**
     * Nothing is read before the hop. A property's setter runs wherever the property put it, so reading the window
     * on the calling thread is the wrong-region touch this exists to avoid; the hop is to the viewer, and the whole
     * repaint sits inside it.
     */
    @Test
    void everythingHappensInsideAHopToTheViewer() {
        MenuHolder holder = openEditor(spec());
        holder.editor().orElseThrow().recordProperty(44, new Fixed("stale", "gone"));
        Deferring scheduler = new Deferring();

        EditorRefresh.reRender(holder, renderer(), scheduler);

        assertThat(scheduler.hoppedTo).containsExactly(viewer);
        assertThat(holder.editor().orElseThrow().propertyAt(44))
                .as("the window is not touched before the hop runs")
                .isPresent();

        scheduler.runQueued();

        assertThat(holder.editor().orElseThrow().propertyAt(44)).isEmpty();
    }

    // -- the window is still open ------------------------------------------------------------------------------

    /**
     * The common case: a toggle wrote a value and the window never closed. The same inventory is repainted, so the
     * viewer is left looking at the object they were already looking at rather than at a second window opened over
     * the first.
     */
    @Test
    void aWindowStillOnTopIsRepaintedInPlaceRatherThanOpenedAgain() {
        EditorSpec spec = spec();
        MenuHolder holder = openEditor(spec);
        Inventory before = holder.getInventory();
        values.set(0, "peaceful");

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(holder.getInventory()).isSameAs(before);
        assertThat(viewer.getOpenInventory().getTopInventory()).isSameAs(before);
    }

    /**
     * The routing is cleared before the repaint, so a property that has moved slot cannot be clicked at the slot it
     * used to be drawn in. A repaint that only overwrote items would leave the old mapping behind.
     */
    @Test
    void theSlotRoutingIsClearedBeforeAnInPlaceRepaint() {
        EditorSpec spec = spec();
        MenuHolder holder = openEditor(spec);
        holder.editor().orElseThrow().recordProperty(44, new Fixed("stale", "gone"));

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(holder.editor().orElseThrow().propertyAt(44)).isEmpty();
        assertThat(holder.editor().orElseThrow().propertyAt(10)).isPresent();
    }

    // -- the window has gone --------------------------------------------------------------------------------------

    /**
     * The back path: a property opened a child window, the child closed, and the viewer is looking at nothing. A
     * fresh window is built and opened on the same holder, which is what "back reopens the parent editor" means. The
     * holder is kept rather than rebuilt, so the listener and the teardown that already own this menu go on owning it.
     */
    @Test
    void aWindowTheViewerHasLeftIsBuiltAgainOnTheSameHolder() {
        EditorSpec spec = spec();
        MenuHolder holder = openEditor(spec);
        Inventory before = holder.getInventory();
        viewer.closeInventory();

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(holder.getInventory()).isNotSameAs(before);
        assertThat(viewer.getOpenInventory().getTopInventory()).isSameAs(holder.getInventory());
        assertThat(holder.getInventory().getHolder()).isSameAs(holder);
    }

    /**
     * The rebuilt window carries the spec's own title, read again against the live subject and centred the way the
     * first open centred it. A rebuild that skipped either step would be visibly a different window from the one the
     * viewer left, which is the opposite of what "back reopens the parent editor" promises.
     */
    @Test
    void theRebuiltWindowIsTitledTheWayTheFirstOpenTitledIt() {
        EditorSpec spec = spec();
        MenuHolder holder = openEditor(spec);
        viewer.closeInventory();

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(plain(viewer.getOpenInventory().title()))
                .isEqualTo(plain(MenuTitles.centre(spec.title(viewer, "home"))));
    }

    /** The rebuilt window is the size the layout asks for, so every slot the layout names has somewhere to go. */
    @Test
    void theRebuiltWindowIsTheSizeTheLayoutAsksFor() {
        EditorSpec spec = spec();
        MenuHolder holder = openEditor(spec);
        viewer.closeInventory();

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(holder.getInventory().getSize()).isEqualTo(spec.layout().rows() * 9);
    }

    /**
     * The routing is cleared for a rebuild too. The state survives the window, so a slot recorded against the window
     * the viewer left would otherwise still answer for a click in the window that replaced it.
     */
    @Test
    void theSlotRoutingIsClearedBeforeARebuildAsWell() {
        MenuHolder holder = openEditor(spec());
        holder.editor().orElseThrow().recordProperty(44, new Fixed("stale", "gone"));
        viewer.closeInventory();

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(holder.editor().orElseThrow().propertyAt(44)).isEmpty();
        assertThat(holder.editor().orElseThrow().propertyAt(10)).isPresent();
    }

    /**
     * A second window of the same menu is somebody else's window. The check is on the holder itself and not on what
     * the holder is a holder of, because two opens of one editor carry two holders that agree about everything
     * except which window each one owns. Painting into the wrong one leaves the viewer clicking a window whose
     * routing belongs to the other.
     */
    @Test
    void aSecondWindowOfTheSameMenuIsStillNotThisHoldersWindow() {
        EditorSpec spec = spec();
        MenuHolder first = openEditor(spec);
        Inventory firstWindow = first.getInventory();
        openEditor(spec);

        EditorRefresh.reRender(first, renderer(), new SameThreadScheduler());

        assertThat(first.getInventory()).isNotSameAs(firstWindow);
        assertThat(viewer.getOpenInventory().getTopInventory()).isSameAs(first.getInventory());
    }

    /** A window opened over the editor belongs to somebody else, so the editor is rebuilt rather than painted into. */
    @Test
    void aWindowThatIsNotThisHoldersIsNotPaintedInto() {
        MenuHolder holder = openEditor(spec());
        Inventory before = holder.getInventory();
        Inventory somebodyElse = MockBukkit.getMock().createInventory(null, 27);
        viewer.openInventory(somebodyElse);

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(holder.getInventory()).isNotSameAs(before);
        assertThat(viewer.getOpenInventory().getTopInventory()).isSameAs(holder.getInventory());
    }

    // -- nothing to repaint ----------------------------------------------------------------------------------------

    /**
     * A holder that carries no editor is not an editor. It stops before the state is read rather than after, which
     * matters because the next thing the repaint does is cast the state's spec to an editor spec: a holder carrying
     * a plain menu spec would fail that cast rather than quietly repaint nothing.
     */
    @Test
    void aHolderWithNoEditorStateRepaintsNothing() {
        MenuHolder holder =
                new MenuHolder("plain", new MenuSpecLoader().parse("rows = 3"), MenuContext.of(viewer, null, 0));
        Inventory before = MockBukkit.getMock().createInventory(holder, 27);
        holder.attach(before);
        viewer.closeInventory();

        assertThatCode(() -> EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler()))
                .doesNotThrowAnyException();
        assertThat(holder.getInventory()).isSameAs(before);
    }

    /**
     * A viewer who left the server between the write and the hop gets no window. The hop itself does not drop the
     * task, because the entity is still the one the scheduler was handed, so the check has to be made here.
     *
     * <p>What is asserted is that nothing happened at all, not that no window was opened. Both repaint branches
     * clear the slot routing before they draw, so routing that is still there is the only evidence that the walk
     * stopped before either branch was chosen.
     */
    @Test
    void aViewerWhoWentOfflineIsNotSentAWindow() {
        MenuHolder holder = openEditor(spec());
        Inventory before = holder.getInventory();
        holder.editor().orElseThrow().recordProperty(44, new Fixed("stale", "gone"));
        viewer.disconnect();

        EditorRefresh.reRender(holder, renderer(), new SameThreadScheduler());

        assertThat(holder.editor().orElseThrow().propertyAt(44)).isPresent();
        assertThat(holder.getInventory()).isSameAs(before);
    }
}

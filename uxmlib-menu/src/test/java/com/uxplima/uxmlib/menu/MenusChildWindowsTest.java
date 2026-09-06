package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.style.MenuTitles;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.property.SelectorButton;
import com.uxplima.uxmlib.menu.render.ConfirmRenderer;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.LastMenu;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The windows the facade opens for code rather than from a file: the property editor, the entity list, the selector,
 * the confirm, the grid canvas and the live preview. A feature calls each of these once, in a place nobody reads
 * again, so what matters is that every one of them builds the same {@link MenuHolder} the one listener routes and the
 * one teardown owns, and shows it on the viewer's own thread. That sameness is the whole reason these openers exist.
 *
 * <p>The preview carries the most, because it is the one window that deliberately does less than a real open: no
 * registration, no requirement gate, no open-actions, no history entry and no refresh timer. Each of those is a thing
 * a later reader would be tempted to add back "for consistency", so each is pinned here.
 */
class MenusChildWindowsTest {

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

    /** A property that reports a fixed label and value, so an editor has something to paint. */
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

    /** Records the hop rather than taking it, so the window can be inspected before and after the hop runs. */
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

    private SameThreadScheduler scheduler;

    private Menus menus;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new SameThreadScheduler();
        menus = engine(scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    private static Menus engine(SameThreadScheduler scheduler) {
        return new Menus(
                renderer(), scheduler, new ListSourceRegistry(), new EditorRenderer(new PlainText(), Theme::defaults));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private Inventory top() {
        return viewer.getOpenInventory().getTopInventory();
    }

    /** MockBukkit hands back a null top inventory once a window is closed, so every read of one goes through here. */
    private @Nullable Object holderOfOpenWindow() {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder();
    }

    private MenuHolder openHolder() {
        Object holder = holderOfOpenWindow();
        assertThat(holder).isInstanceOf(MenuHolder.class);
        return (MenuHolder) holder;
    }

    private static EditorSpec editorSpec() {
        return EditorSpec.builder()
                .layout(EntityEditorLayout.codeDefault(List.of(10, 12), 22))
                .title((who, subject) -> Component.text("editing " + subject))
                .valueLore("value: %value%")
                .backName("back")
                .properties(subject -> List.of(new Fixed("difficulty", "hard")))
                .onBack(who -> {})
                .build();
    }

    private static EntityListSpec listSpec(int rows) {
        return EntityListSpec.builder()
                .title(Component.text("Warps"))
                .rows(rows)
                .contentSlots(List.of(0, 1, 2))
                .navigation(9, 17, Material.ARROW)
                .navNames(Component.text("Back"), Component.text("Next"))
                .filler(Material.GRAY_STAINED_GLASS_PANE)
                .entities(() -> List.of("spawn", "shop"))
                .iconRenderer((who, entity) -> new ItemStack(Material.STONE))
                .onSelect((who, entity) -> {})
                .build();
    }

    /** A canvas whose clicks do nothing: the grid tests are about the window the opener builds, not its editing. */
    private static GridHandlers readOnlyHandlers() {
        return new GridHandlers((view, player, menuSlot, filled, kind) -> {});
    }

    private static GridSpec gridSpec(int menuRows) {
        return new GridSpec(
                Component.text("Canvas"),
                menuRows,
                Map::of,
                new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE),
                new ItemStack(Material.BARRIER),
                new ItemStack(Material.ARROW),
                new ItemStack(Material.ARROW),
                List.of());
    }

    // -- the editor --------------------------------------------------------------------------------------------

    /**
     * An engine with no editor renderer cannot paint an editor, so asking it to open one is a wiring mistake in the
     * plugin rather than anything an operator can cause. It fails on the calling thread, before any hop, so the stack
     * names the caller; a half-open empty window would be far harder to trace back.
     */
    @Test
    void anEngineWiredWithoutAnEditorRendererRefusesRatherThanOpeningAnEmptyWindow() {
        Deferring deferring = new Deferring();
        Menus plain = new Menus(renderer(), deferring, new ListSourceRegistry());

        assertThatIllegalStateException()
                .isThrownBy(() -> plain.openEditor(viewer, editorSpec(), "home"))
                .withMessageContaining("editor");
        assertThat(deferring.hoppedTo).isEmpty();
        assertThat(holderOfOpenWindow()).isNull();
    }

    /**
     * An editor is not a window system of its own: it is the one holder every other menu uses, tagged with the state
     * that routes its clicks. If it stopped being one, the one listener would stop recognising it and every click in
     * an editor would fall through to the player's inventory.
     */
    @Test
    void theEditorWindowIsTheOneHolderTaggedWithItsEditorState() {
        EditorSpec spec = editorSpec();

        menus.openEditor(viewer, spec, "home");

        MenuHolder holder = openHolder();
        assertThat(holder.editor()).isPresent();
        assertThat(holder.editor().orElseThrow().spec()).isSameAs(spec);
        assertThat(holder.editor().orElseThrow().subject()).isEqualTo("home");
    }

    /** The window is the size the layout asks for, so every slot the layout names has somewhere to go. */
    @Test
    void theEditorWindowIsTheSizeItsLayoutAsksFor() {
        EditorSpec spec = editorSpec();

        menus.openEditor(viewer, spec, "home");

        assertThat(top().getSize()).isEqualTo(spec.layout().rows() * 9);
        assertThat(plain(viewer.getOpenInventory().title()))
                .isEqualTo(plain(MenuTitles.centre(spec.title(viewer, "home"))));
    }

    /**
     * Nothing touches the live inventory before the hop. Opening an editor is called from wherever the feature happens
     * to be, and touching a player's window off their own thread is the region mistake this hop exists to avoid.
     */
    @Test
    void theEditorIsBuiltAndShownInsideAHopToTheViewer() {
        Deferring deferring = new Deferring();
        Menus deferred = engine(deferring);

        deferred.openEditor(viewer, editorSpec(), "home");

        assertThat(holderOfOpenWindow()).isNull();
        assertThat(deferring.hoppedTo).containsExactly(viewer);

        deferring.runQueued();

        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);
    }

    /** A viewer can leave between the call and the hop; opening a window for them then is a leak, not a courtesy. */
    @Test
    void anEditorForAViewerWhoLeftBeforeTheHopOpensNothing() {
        Deferring deferring = new Deferring();
        Menus deferred = engine(deferring);
        deferred.openEditor(viewer, editorSpec(), "home");

        viewer.disconnect();
        deferring.runQueued();

        assertThat(holderOfOpenWindow()).isNull();
    }

    // -- the entity list ---------------------------------------------------------------------------------------

    /** The same holder rule the editor keeps, with the state that routes a list's entity, nav and button slots. */
    @Test
    void theListWindowIsTheOneHolderTaggedWithItsListState() {
        EntityListSpec spec = listSpec(3);

        menus.openList(viewer, spec);

        MenuHolder holder = openHolder();
        assertThat(holder.listView()).isPresent();
        assertThat(holder.listView().orElseThrow().spec()).isSameAs(spec);
        assertThat(top().getSize()).isEqualTo(27);
        assertThat(plain(viewer.getOpenInventory().title())).isEqualTo(plain(MenuTitles.centre(spec.title())));
    }

    /**
     * A list re-paginates the same holder on a page flip, so it arms no timer. The scheduler here refuses a repeating
     * hop, so a list that started arming one would fail loudly rather than leak a task per open.
     */
    @Test
    void aListArmsNoRefreshTimer() {
        assertThatCode(() -> menus.openList(viewer, listSpec(3))).doesNotThrowAnyException();
    }

    /** The same offline rule every opener keeps: a viewer who left between the call and the hop gets no window. */
    @Test
    void aListForAViewerWhoLeftBeforeTheHopOpensNothing() {
        Deferring deferring = new Deferring();
        Menus deferred = engine(deferring);
        deferred.openList(viewer, listSpec(3));

        viewer.disconnect();
        deferring.runQueued();

        assertThat(holderOfOpenWindow()).isNull();
    }

    // -- the selector ------------------------------------------------------------------------------------------

    /**
     * A row count no inventory has is a caller mistake, and it is refused before the hop rather than inside it: a
     * failure inside the hop would be raised on someone else's thread, where the caller cannot catch it and the stack
     * no longer names them.
     */
    @Test
    void aSelectorWithARowCountNoInventoryHasIsRefusedBeforeAnyHopIsTaken() {
        Deferring deferring = new Deferring();
        Menus deferred = engine(deferring);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> deferred.openSelector(viewer, Component.text("Pick"), 7, Material.AIR, List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> deferred.openSelector(viewer, Component.text("Pick"), 0, Material.AIR, List.of()));
        assertThat(deferring.hoppedTo).isEmpty();
    }

    /** Each option is routed by the slot it was drawn at, which is how a click finds the choice the viewer made. */
    @Test
    void eachSelectorOptionIsRoutedByTheSlotItWasDrawnAt() {
        List<String> chosen = new ArrayList<>();
        List<SelectorButton> buttons = List.of(
                SelectorButton.of(2, new ItemStack(Material.DIAMOND), () -> chosen.add("diamond")),
                SelectorButton.of(6, new ItemStack(Material.EMERALD), () -> chosen.add("emerald")));

        menus.openSelector(viewer, Component.text("Pick"), 1, Material.GRAY_STAINED_GLASS_PANE, buttons);

        MenuHolder holder = openHolder();
        assertThat(holder.selector()).isPresent();
        assertThat(holder.selector().orElseThrow().chooseAt(2)).isPresent();
        assertThat(holder.selector().orElseThrow().chooseAt(6)).isPresent();
        assertThat(holder.selector().orElseThrow().chooseAt(4)).isEmpty();
        assertThat(chosen).isEmpty();

        holder.selector().orElseThrow().chooseAt(6).orElseThrow().onClick(false, false);
        assertThat(chosen).containsExactly("emerald");
    }

    /** The opener a property is handed is this facade's own selector, not a second window system beside it. */
    @Test
    void theSelectorOpenerHandedToAPropertyOpensTheEnginesOwnChild() {
        menus.selectorOpener()
                .openSelector(
                        viewer,
                        Component.text("Pick"),
                        1,
                        Material.GRAY_STAINED_GLASS_PANE,
                        List.of(SelectorButton.of(0, new ItemStack(Material.DIAMOND), () -> {})));

        assertThat(openHolder().selector()).isPresent();
    }

    // -- the confirm -------------------------------------------------------------------------------------------

    /** A confirm is the same holder too, and neither decision runs until the viewer picks one. */
    @Test
    void theConfirmWindowIsTheOneHolderAndNeitherDecisionRunsUntilAButtonIsClicked() {
        List<String> decided = new ArrayList<>();

        menus.confirm(viewer, Component.text("Sure?"), () -> decided.add("yes"), () -> decided.add("no"));

        MenuHolder holder = openHolder();
        assertThat(holder.confirm()).isPresent();
        assertThat(top().getSize()).isEqualTo(ConfirmRenderer.ROWS * 9);
        assertThat(top().getItem(ConfirmRenderer.YES_SLOT)).isNotNull();
        assertThat(top().getItem(ConfirmRenderer.NO_SLOT)).isNotNull();
        assertThat(decided).isEmpty();

        holder.confirm()
                .orElseThrow()
                .decisionAt(ConfirmRenderer.YES_SLOT)
                .orElseThrow()
                .run();
        assertThat(decided).containsExactly("yes");
    }

    /** The opener a property is handed for a destructive step is this facade's own confirm. */
    @Test
    void theConfirmOpenerHandedToAPropertyOpensTheEnginesOwnChild() {
        menus.confirmOpener().openConfirm(viewer, Component.text("Sure?"), () -> {}, () -> {});

        assertThat(openHolder().confirm()).isPresent();
    }

    // -- the grid canvas ---------------------------------------------------------------------------------------

    /**
     * The grid window is one row taller than the canvas it edits: the extra row is the control strip. A window built
     * to the canvas height would draw the controls over the last row of editable slots.
     */
    @Test
    void theGridWindowIsOneRowTallerThanTheCanvasItEdits() {
        menus.openGrid(viewer, gridSpec(3), readOnlyHandlers());

        assertThat(top().getSize()).isEqualTo(4 * 9);
        assertThat(openHolder().gridView()).isPresent();
    }

    /** A canvas already six rows tall cannot grow, so the control strip shares the last row rather than overflowing. */
    @Test
    void aSixRowCanvasKeepsTheWindowAtSixRows() {
        menus.openGrid(viewer, gridSpec(6), readOnlyHandlers());

        assertThat(top().getSize()).isEqualTo(6 * 9);
    }

    // -- the preview -------------------------------------------------------------------------------------------

    /**
     * A preview is a look, not an open. Registering it would leave the working copy openable by id long after the
     * operator abandoned the edit, so the engine still answers that it knows no such menu.
     */
    @Test
    void aPreviewRegistersNothingSoTheEngineStillKnowsNoSuchMenu() {
        menus.openPreview(viewer, new MenuSpecLoader().parse("rows = 3"), () -> {});

        assertThat(menus.registeredSpec("preview")).isEmpty();
        assertThat(openHolder().spec().rows()).isEqualTo(3);
    }

    /**
     * A preview arms no refresh timer even when the spec asks for one. The scheduler here refuses a repeating hop, so
     * the pair says it plainly: really opening the same spec takes the timer, previewing it does not.
     */
    @Test
    void aPreviewArmsNoRefreshTimerEvenWhenTheSpecAsksForOne() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 3\nrefresh { enabled = true, interval-ticks = 20 }");

        assertThatCode(() -> menus.openPreview(viewer, spec, () -> {})).doesNotThrowAnyException();

        menus.registerSpec("live", spec);
        assertThatThrownBy(() -> menus.open(viewer, "live", null)).isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * A preview neither gates on the spec's open-requirement nor fires its open-actions. An operator previewing a shop
     * they cannot afford to enter must still see it, and a preview that fired the open-actions would pay out rewards
     * for a menu nobody opened.
     */
    @Test
    void aPreviewSkipsTheOpenRequirementAndFiresNoOpenActions() {
        List<String> fired = new ArrayList<>();
        ActionRegistry actions = new ActionRegistry();
        actions.register("pay", ctx -> fired.add("pay"));
        ConditionRegistry conditions = new ConditionRegistry();
        conditions.register("never", (ctx, args) -> false);
        Menus gated = new Menus(renderer(), scheduler, new ListSourceRegistry(), null, actions, conditions);
        MenuSpec spec =
                new MenuSpecLoader().parse("rows = 3\nopen-requirement = [ \"never\" ]\nopen-actions = [ \"pay\" ]");

        gated.openPreview(viewer, spec, () -> {});

        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);
        assertThat(fired).isEmpty();
    }

    /** A preview is not somewhere the viewer can step back to, so it never joins the reopen history. */
    @Test
    void aPreviewIsNotRecordedAsSomewhereToStepBackTo() {
        LastMenu history = new LastMenu();
        Menus tracked = new Menus(renderer(), scheduler, new ListSourceRegistry(), null, null, null, history);

        tracked.openPreview(viewer, new MenuSpecLoader().parse("rows = 3"), () -> {});

        assertThat(tracked.lastMenuId(viewer.getUniqueId())).isEmpty();
    }

    /**
     * A preview paints no bottom inventory even when the spec declares one. The operator is looking at their own
     * items below the window, and overwriting them for a look they did not open is not recoverable by closing it.
     */
    @Test
    void aPreviewPaintsNoBottomInventoryEvenWhenTheSpecDeclaresOne() {
        viewer.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
        MenuSpec spec = new MenuSpecLoader()
                .parse("rows = 3\nbottom-inventory = true\n"
                        + "items { tile { slot = 81, material = GRAY_STAINED_GLASS_PANE, name = \" \" } }");

        menus.openPreview(viewer, spec, () -> {});

        assertThat(viewer.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.DIAMOND, 5));
    }

    /** The runnable handed to the preview is the hook the close path runs, which is how a preview steps back. */
    @Test
    void theRunnableHandedToAPreviewIsTheHookItsCloseRuns() {
        List<String> stepped = new ArrayList<>();

        menus.openPreview(viewer, new MenuSpecLoader().parse("rows = 3"), () -> stepped.add("back"));
        openHolder().fireCloseHook();

        assertThat(stepped).containsExactly("back");
    }
}

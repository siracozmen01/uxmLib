package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.LastMenu;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The alternative render at the open choke-point: a Floodgate viewer gets a native form where a Java viewer gets a
 * chest. It is the one place in the engine where the same open produces two different things, and nothing in this
 * module tested it: the whole branch, its button building, its paging and its confirm modal were reached only on a
 * live server with Floodgate installed.
 *
 * <p>A form is an alternative render, not a second window system: it builds no holder and arms no refresh, but it is
 * still an open, so it records into the back history and fires the menu's open-actions exactly as the chest path does.
 * Both halves of that sentence are asserted here, because a later reader tidying the branch could break either.
 */
class MenusBedrockFormTest {

    /** A catalogue that hands every key straight back, so a form's button text is readable in an assertion. */
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

    /** Answers for exactly the players a test names, so one engine can serve a Bedrock and a Java viewer at once. */
    private static final class Detector implements BedrockDetector {

        private final Set<UUID> bedrock = new HashSet<>();

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock.contains(player);
        }
    }

    /** Keeps the last form of each kind and the callbacks it was sent with, so a tap can be replayed by hand. */
    private static final class RecordingScreen implements BedrockScreen {

        private final List<String> sent = new ArrayList<>();

        private @Nullable String title;

        private @Nullable String content;

        private @Nullable List<BedrockButton> buttons;

        private @Nullable IntConsumer onSelect;

        private @Nullable List<BedrockWidget> widgets;

        private @Nullable Consumer<Map<String, String>> onSubmit;

        private @Nullable Runnable onClose;

        private @Nullable String button1;

        private @Nullable String button2;

        private @Nullable Runnable onButton1;

        private @Nullable Runnable onButton2;

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {
            sent.add("simple");
            this.title = title;
            this.content = content;
            this.buttons = buttons;
            this.onSelect = onSelect;
        }

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {
            sent.add("modal");
            this.title = title;
            this.button1 = button1;
            this.button2 = button2;
            this.onButton1 = onButton1;
            this.onButton2 = onButton2;
        }

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {
            sent.add("input");
        }

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {
            sent.add("custom");
            this.title = title;
            this.content = content;
            this.widgets = widgets;
            this.onSubmit = onSubmit;
            this.onClose = onClose;
        }

        private List<String> buttonTexts() {
            return Objects.requireNonNull(buttons, "buttons").stream()
                    .map(BedrockButton::text)
                    .toList();
        }

        private void tap(int index) {
            Objects.requireNonNull(onSelect, "onSelect").accept(index);
        }
    }

    /** Records the hop rather than taking it, so a form response can be seen before it reaches the viewer. */
    private static final class Deferring extends SameThreadScheduler {

        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public com.uxplima.uxmlib.scheduler.TaskHandle entity(org.bukkit.entity.Entity entity, Runnable task) {
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

    private Detector detector;

    private RecordingScreen screen;

    private ListSourceRegistry lists;

    private ActionRegistry actions;

    private LastMenu history;

    private final List<MenuActionContext> ran = new ArrayList<>();

    private Menus menus;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new SameThreadScheduler();
        detector = new Detector();
        detector.bedrock.add(viewer.getUniqueId());
        screen = new RecordingScreen();
        lists = new ListSourceRegistry();
        actions = new ActionRegistry();
        actions.register("note", ran::add);
        history = new LastMenu();
        menus = engine(scheduler);
    }

    /** The same wiring the fixture uses, on whichever scheduler the test needs. */
    private Menus engine(SameThreadScheduler on) {
        return new Menus(renderer(), on, lists, null, actions, new ConditionRegistry(), history, detector, screen);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    private void open(String id, String hocon) {
        menus.registerSpec(id, new MenuSpecLoader().parse(hocon));
        menus.open(viewer, id, null);
    }

    /** MockBukkit hands back a null top inventory once a window is closed, so every read of one goes through here. */
    private @Nullable Object holderOfOpenWindow() {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder();
    }

    // -- which render the open chooses ---------------------------------------------------------------------------

    /**
     * The whole point of the branch: a Bedrock viewer gets a form and no chest. A window opened alongside the form
     * would sit behind it, unreachable and holding a holder the listener would go on routing.
     */
    @Test
    void aBedrockViewerGetsAFormAndNoChestWindow() {
        open("shop", "rows = 1\nitems { a { slot = 0, material = STONE, name = \"A\", click { left = [\"note\"] } } }");

        assertThat(screen.sent).containsExactly("simple");
        assertThat(holderOfOpenWindow()).isNull();
    }

    /** The same engine, a viewer Floodgate does not claim: the chest path, unchanged and with no form sent. */
    @Test
    void aJavaViewerOnTheSameEngineStillGetsTheChest() {
        PlayerMock java = MockBukkit.getMock().addPlayer();
        menus.registerSpec("shop", new MenuSpecLoader().parse("rows = 1\nitems { a { slot = 0, material = STONE } }"));

        menus.open(java, "shop", null);

        assertThat(screen.sent).isEmpty();
        assertThat(java.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    /** A menu that says it cannot be a form is not made one: an item-display menu a form cannot represent. */
    @Test
    void aChestOnlyMenuKeepsABedrockViewerOnTheChest() {
        open("shop", "chest-only = true\nrows = 1\nitems { a { slot = 0, material = STONE } }");

        assertThat(screen.sent).isEmpty();
        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);
    }

    /** A menu that declares its own form gets that one, not the button list the engine would have degraded to. */
    @Test
    void aMenuThatDeclaresItsOwnFormSendsThatRatherThanTheDegradedButtonList() {
        open(
                "warp",
                """
                rows = 1
                items { a { slot = 0, material = STONE, name = "A" } }
                bedrock {
                  title = "Create Warp"
                  content = "Fill it in"
                  widgets = [ { type = toggle, name = public, label = "Public?", default = true } ]
                  on-submit = [ "note" ]
                }
                """);

        assertThat(screen.sent).containsExactly("custom");
        assertThat(screen.title).isEqualTo("Create Warp");
        assertThat(screen.content).isEqualTo("Fill it in");
        assertThat(screen.widgets).containsExactly(new BedrockWidget.Toggle("public", "Public?", true));
    }

    // -- a form is still an open ---------------------------------------------------------------------------------

    /**
     * A form carries no window, so it builds no holder; but it is still an open. If it stopped recording, a back from
     * a form would have nothing to step to, and if it stopped firing the open-actions, a Bedrock viewer would silently
     * miss a menu's sound, message or gate.
     */
    @Test
    void aFormOpenStillJoinsTheBackHistoryAndFiresTheOpenActions() {
        open("shop", "rows = 1\nopen-actions = [ \"note\" ]\nitems { a { slot = 0, material = STONE } }");

        assertThat(menus.lastMenuId(viewer.getUniqueId())).hasValue("shop");
        assertThat(ran).hasSize(1);
    }

    /** The declared-form path shares that bookkeeping, and shares it through the same one place. */
    @Test
    void aDeclaredFormOpenJoinsTheBackHistoryAndFiresTheOpenActionsToo() {
        open(
                "warp",
                """
                rows = 1
                open-actions = [ "note" ]
                items { a { slot = 0, material = STONE } }
                bedrock { title = "T", widgets = [ { type = toggle, name = ok, label = "OK" } ] }
                """);

        assertThat(menus.lastMenuId(viewer.getUniqueId())).hasValue("warp");
        assertThat(ran).hasSize(1);
    }

    // -- the buttons the degraded form carries -------------------------------------------------------------------

    /** One button per visible actionable item, in slot order: the reading order the chest gives a Java viewer. */
    @Test
    void everyActionableItemBecomesOneButtonInSlotOrder() {
        open(
                "shop",
                """
                rows = 1
                items {
                  c { slot = 2, material = STONE, name = "C", click { left = ["note"] } }
                  a { slot = 0, material = STONE, name = "A", click { left = ["note"] } }
                  b { slot = 1, material = STONE, name = "B", click { left = ["note"] } }
                }
                """);

        assertThat(screen.buttonTexts()).containsExactly("A", "B", "C");
    }

    /** A tap runs that button's own item's left-click actions: the same handler the chest click would reach. */
    @Test
    void tappingAButtonRunsThatItemsLeftClickActions() {
        open(
                "shop",
                """
                rows = 1
                items {
                  a { slot = 0, material = STONE, name = "A", click { left = ["note:a"] } }
                  b { slot = 1, material = STONE, name = "B", click { left = ["note:b"] } }
                }
                """);

        screen.tap(1);

        assertThat(ran).hasSize(1);
        assertThat(ran.get(0).arg()).isEqualTo("b");
    }

    /**
     * Cumulus reports a dismissal as an index outside the button list, so the range check is the difference between a
     * viewer closing a form and the server throwing on their behalf.
     */
    @Test
    void aTapOutsideTheButtonRangeRunsNothingRatherThanFailing() {
        open("shop", "rows = 1\nitems { a { slot = 0, material = STONE, name = \"A\", click { left = [\"note\"] } } }");

        assertThatCode(() -> {
                    screen.tap(-1);
                    screen.tap(99);
                })
                .doesNotThrowAnyException();
        assertThat(ran).isEmpty();
    }

    // -- paging a list-backed menu as buttons --------------------------------------------------------------------

    /** A list's entries become buttons after the static ones, one page at a time, with a Next when more remain. */
    @Test
    void aListBackedMenuPagesItsEntriesAsButtonsAndOffersTheNextPage() {
        lists.register("warps", ctx -> List.of("spawn", "shop", "mine"));
        open(
                "warps",
                """
                rows = 1
                items {
                  go { slot = 0, material = STONE, name = "go", click { left = ["note"] } }
                  grid { slots = [1, 2], list { source = warps, template { material = PAPER, name = "warp" } } }
                }
                """);

        assertThat(screen.buttonTexts()).containsExactly("go", "warp", "warp", MenuKeys.PAGE_NEXT);
    }

    /** Tapping Next re-sends the form one page over, so the last entry is reachable without a chest. */
    @Test
    void tappingNextResendsTheFormOnePageOver() {
        lists.register("warps", ctx -> List.of("spawn", "shop", "mine"));
        open(
                "warps",
                """
                rows = 1
                items {
                  grid { slots = [0, 1], list { source = warps, template { material = PAPER, name = "warp" } } }
                }
                """);

        screen.tap(2);

        assertThat(screen.sent).containsExactly("simple", "simple");
        assertThat(screen.buttonTexts()).containsExactly("warp", MenuKeys.PAGE_PREVIOUS);
    }

    // -- the declared form's submit ------------------------------------------------------------------------------

    /** Each submitted value reaches the on-submit actions as a local placeholder, keyed by the widget's own name. */
    @Test
    void submittingBindsEachValueAsALocalPlaceholderForTheOnSubmitActions() {
        open(
                "warp",
                """
                rows = 1
                items { a { slot = 0, material = STONE } }
                bedrock {
                  title = "Create Warp"
                  widgets = [ { type = input, name = warpname, label = "Name" } ]
                  on-submit = [ "note:%warpname%" ]
                }
                """);

        Objects.requireNonNull(screen.onSubmit, "onSubmit").accept(Map.of("warpname", "spawn"));

        assertThat(ran).hasSize(1);
        assertThat(ran.get(0).arg()).isEqualTo("spawn");
    }

    /** A viewer who dismisses the form submitted nothing, so nothing runs: the close is not a submit with no values. */
    @Test
    void dismissingTheDeclaredFormRunsNothing() {
        open(
                "warp",
                """
                rows = 1
                items { a { slot = 0, material = STONE } }
                bedrock {
                  title = "Create Warp"
                  widgets = [ { type = input, name = warpname, label = "Name" } ]
                  on-submit = [ "note" ]
                }
                """);

        Objects.requireNonNull(screen.onClose, "onClose").run();

        assertThat(ran).isEmpty();
    }

    // -- the confirm modal ---------------------------------------------------------------------------------------

    /**
     * A confirm is a plain two-choice prompt, so it is always safe to render as a form and the redirect is
     * unconditional. The chest it replaces carries its answer in lime and red wool, which a form could not label.
     */
    @Test
    void aBedrockViewerGetsAModalConfirmRatherThanTheConfirmChest() {
        List<String> decided = new ArrayList<>();

        menus.confirm(viewer, Component.text("Sure?"), () -> decided.add("yes"), () -> decided.add("no"));

        assertThat(screen.sent).containsExactly("modal");
        assertThat(holderOfOpenWindow()).isNull();
        assertThat(screen.button1).isEqualTo(MenuKeys.CONFIRM_YES);
        assertThat(screen.button2).isEqualTo(MenuKeys.CONFIRM_NO);
        assertThat(decided).isEmpty();

        Objects.requireNonNull(screen.onButton1, "onButton1").run();
        assertThat(decided).containsExactly("yes");
    }

    /** The decline button is wired to the other decision, which is the half a copied line would get wrong. */
    @Test
    void decliningTheModalRunsTheOtherDecision() {
        List<String> decided = new ArrayList<>();

        menus.confirm(viewer, Component.text("Sure?"), () -> decided.add("yes"), () -> decided.add("no"));
        Objects.requireNonNull(screen.onButton2, "onButton2").run();

        assertThat(decided).containsExactly("no");
    }

    // -- what arrives off the main thread -------------------------------------------------------------------------

    /**
     * Cumulus answers a form on its own thread, so every handler hops before it touches the world. Under a scheduler
     * that runs everything inline the hop and its absence look identical, so this one queues instead: the tap must
     * leave the work waiting for the viewer's thread rather than doing it where the response arrived.
     */
    @Test
    void aFormTapRunsItsActionsOnTheViewersThreadAndNotOnTheResponseThread() {
        Deferring deferring = new Deferring();
        Menus deferred = engine(deferring);
        deferred.registerSpec(
                "shop",
                new MenuSpecLoader()
                        .parse(
                                "rows = 1\nitems { a { slot = 0, material = STONE, name = \"A\", click { left = [\"note\"] } } }"));
        deferred.open(viewer, "shop", null);
        deferring.runQueued();

        screen.tap(0);

        assertThat(ran)
                .as("the tap arrived off the main thread, so nothing may have run yet")
                .isEmpty();

        deferring.runQueued();

        assertThat(ran).hasSize(1);
    }

    /** A submit arrives on the same foreign thread as a tap, and takes the same hop before its actions run. */
    @Test
    void aFormSubmitRunsItsActionsOnTheViewersThreadAndNotOnTheResponseThread() {
        Deferring deferring = new Deferring();
        Menus deferred = engine(deferring);
        deferred.registerSpec(
                "warp",
                new MenuSpecLoader()
                        .parse(
                                """
                                rows = 1
                                items { a { slot = 0, material = STONE } }
                                bedrock {
                                  title = "Create Warp"
                                  widgets = [ { type = input, name = warpname, label = "Name" } ]
                                  on-submit = [ "note:%warpname%" ]
                                }
                                """));
        deferred.open(viewer, "warp", null);
        deferring.runQueued();

        Objects.requireNonNull(screen.onSubmit, "onSubmit").accept(Map.of("warpname", "spawn"));

        assertThat(ran).isEmpty();

        deferring.runQueued();

        assertThat(ran).hasSize(1);
        assertThat(ran.get(0).arg()).isEqualTo("spawn");
    }

    /** The confirm modal's two decisions take that hop as well, for the same reason and on the same thread. */
    @Test
    void aModalDecisionRunsOnTheViewersThreadAndNotOnTheResponseThread() {
        Deferring deferring = new Deferring();
        Menus deferred = engine(deferring);
        List<String> decided = new ArrayList<>();

        deferred.confirm(viewer, Component.text("Sure?"), () -> decided.add("yes"), () -> decided.add("no"));
        deferring.runQueued();

        Objects.requireNonNull(screen.onButton1, "onButton1").run();

        assertThat(decided).isEmpty();

        deferring.runQueued();

        assertThat(decided).containsExactly("yes");
    }
}

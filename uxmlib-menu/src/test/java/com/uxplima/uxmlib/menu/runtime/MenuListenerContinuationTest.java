package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A gesture's action list is walked synchronously until it meets a step whose answer arrives later. An {@code input:}
 * step takes ownership of everything after it and re-enters the walk when the viewer submits; a {@code confirm:} step
 * carries its own two lists and continues neither.
 *
 * <p>The split is what these tests are about, and it is only visible from both sides: what ran before the step, and
 * what ran after it or did not. A test that only checked the submit path would pass with a chain that ran the tail
 * twice, and one that only checked the cancel path would pass with a chain that never suspended at all.
 */
class MenuListenerContinuationTest {

    /** A catalogue that hands every key straight back, so a resolved prompt is readable in an assertion. */
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

    /** One prompt, recorded. A test decides afterwards whether the viewer submits a line or walks away. */
    private static final class RecordingPrompt implements MenuTextPrompt {

        final List<String> keys = new ArrayList<>();

        final List<String> prompts = new ArrayList<>();

        @Nullable String prefill;

        @Nullable Consumer<String> onSubmit;

        @Nullable Runnable onCancel;

        @Override
        public void prompt(
                Player viewer,
                String key,
                Component prompt,
                @Nullable String initialText,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            keys.add(key);
            prompts.add(PlainTextComponentSerializer.plainText().serialize(prompt));
            this.prefill = initialText;
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }
    }

    /** One confirm, recorded, so a test can answer it either way. */
    private static final class RecordingConfirm implements com.uxplima.uxmlib.menu.property.ConfirmOpener {

        final List<String> titles = new ArrayList<>();

        @Nullable Runnable onYes;

        @Nullable Runnable onNo;

        @Override
        public void openConfirm(Player viewer, Component title, Runnable onYes, Runnable onNo) {
            titles.add(PlainTextComponentSerializer.plainText().serialize(title));
            this.onYes = onYes;
            this.onNo = onNo;
        }
    }

    private final List<String> fired = new ArrayList<>();

    private final List<String> sawInput = new ArrayList<>();

    private final RecordingPrompt prompt = new RecordingPrompt();

    private final RecordingConfirm confirm = new RecordingConfirm();

    private Menus menus;

    private Player viewer;

    private Plugin plugin;

    private ActionRegistry actions;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        actions = new ActionRegistry();
        for (String id : List.of("one", "two", "three", "yes", "no", "cancelled")) {
            actions.register(id, ctx -> fired.add(id));
        }
        // Records the %input% the continuation exposes, so a test can assert the typed line reached the tail.
        actions.register("reads-input", ctx -> {
            fired.add("reads-input");
            sawInput.add(ctx.context().localPlaceholders().getOrDefault("input", "<none>"));
        });
        menus = new Menus(renderer(), new SameThreadScheduler(), new ListSourceRegistry());
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    private MenuListener listener(@Nullable MenuTextPrompt textPrompt, boolean withConfirm) {
        return new MenuListener(
                renderer(),
                actions,
                new ConditionRegistry(),
                new SameThreadScheduler(),
                plugin,
                null,
                null,
                withConfirm ? confirm : null,
                0L,
                () -> 1_000_000L,
                new PagedListSourceRegistry(),
                textPrompt,
                null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Register {@code gesture} as the item's left click, open the menu, and click it. */
    private void clickWithLeft(String gesture, MenuListener listener) {
        String hocon = "rows = 3\nitems { go { slot = 4, material = DIAMOND, click { left = " + gesture + " } } }";
        menus.registerSpec("menu", new MenuSpecLoader().parse(hocon));
        menus.open(viewer, "menu", null);
        InventoryView view = viewer.getOpenInventory();
        listener.onClick(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 4, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    // -- an input step splits the chain --------------------------------------------------------------------

    /**
     * Everything before the step runs inline and everything after it waits. Asserting only the first half would pass
     * against a chain that never suspended, so both are asserted from the one click.
     */
    @Test
    void theRefsBeforeAnInputStepRunAndTheRefsAfterItWait() {
        clickWithLeft(
                """
                [ "one", { do = "input:name", prompt = "type-a-name" }, "three" ]
                """,
                listener(prompt, false));

        assertThat(fired).containsExactly("one");
        assertThat(prompt.keys).containsExactly("name");
    }

    @Test
    void submittingTheLineRunsTheRestOfTheChain() {
        clickWithLeft(
                """
                [ "one", { do = "input:name", prompt = "type-a-name" }, "three" ]
                """,
                listener(prompt, false));

        java.util.Objects.requireNonNull(prompt.onSubmit).accept("Steve");

        assertThat(fired).containsExactly("one", "three");
    }

    /** The typed line reaches the tail as %input%, which is the whole point of splitting the chain rather than ending it. */
    @Test
    void theTypedLineIsExposedToTheRefsThatFollow() {
        clickWithLeft(
                """
                [ { do = "input:name", prompt = "type-a-name" }, "reads-input" ]
                """,
                listener(prompt, false));

        java.util.Objects.requireNonNull(prompt.onSubmit).accept("Steve");

        assertThat(sawInput).containsExactly("Steve");
    }

    /** A cancel abandons the tail and runs the step's own deny list instead. Both halves matter. */
    @Test
    void cancellingRunsTheStepsDenyListAndAbandonsTheRest() {
        clickWithLeft(
                """
                [ "one", { do = "input:name", prompt = "type-a-name", deny = ["cancelled"] }, "three" ]
                """,
                listener(prompt, false));

        java.util.Objects.requireNonNull(prompt.onCancel).run();

        assertThat(fired).containsExactly("one", "cancelled");
    }

    @Test
    void aStepWithNoDenyListCancelsQuietlyAndStillAbandonsTheRest() {
        clickWithLeft(
                """
                [ "one", { do = "input:name", prompt = "type-a-name" }, "three" ]
                """,
                listener(prompt, false));

        java.util.Objects.requireNonNull(prompt.onCancel).run();

        assertThat(fired).containsExactly("one");
    }

    @Test
    void thePromptCarriesTheOperatorsLabelAndPreFillResolvedForTheViewer() {
        clickWithLeft(
                """
                [ { do = "input:name", prompt = "type-a-name", default = "Steve" } ]
                """,
                listener(prompt, false));

        assertThat(prompt.prompts).containsExactly("type-a-name");
        assertThat(prompt.prefill).isEqualTo("Steve");
    }

    /** A blank pre-fill is no pre-fill: the field opens empty rather than holding a space. */
    @Test
    void aBlankDefaultLeavesTheFieldEmptyRatherThanPreFillingNothing() {
        clickWithLeft(
                """
                [ { do = "input:name", prompt = "type-a-name" } ]
                """,
                listener(prompt, false));

        assertThat(prompt.prefill).isNull();
    }

    /**
     * An engine wired without a text prompt cannot prompt. It runs the cancel refs rather than dead-ending, so a menu
     * that reaches an input step on a host that never wired one still finishes its gesture.
     */
    @Test
    void anEngineWithNoPromptRunsTheDenyListRatherThanStopping() {
        clickWithLeft(
                """
                [ "one", { do = "input:name", prompt = "type-a-name", deny = ["cancelled"] }, "three" ]
                """,
                listener(null, false));

        assertThat(fired).containsExactly("one", "cancelled");
    }

    // -- a confirm step branches rather than continuing -----------------------------------------------------

    @Test
    void aConfirmStepOpensTheWindowAndRunsNeitherBranchYet() {
        clickWithLeft(
                """
                [ "one", { do = "confirm:drop", title = "sure", yes = ["yes"], no = ["no"] } ]
                """,
                listener(null, true));

        assertThat(fired).containsExactly("one");
        assertThat(confirm.titles).containsExactly("sure");
    }

    @Test
    void acceptingRunsTheYesBranchOnly() {
        clickWithLeft(
                """
                [ { do = "confirm:drop", title = "sure", yes = ["yes"], no = ["no"] } ]
                """,
                listener(null, true));

        java.util.Objects.requireNonNull(confirm.onYes).run();

        assertThat(fired).containsExactly("yes");
    }

    @Test
    void decliningRunsTheNoBranchOnly() {
        clickWithLeft(
                """
                [ { do = "confirm:drop", title = "sure", yes = ["yes"], no = ["no"] } ]
                """,
                listener(null, true));

        java.util.Objects.requireNonNull(confirm.onNo).run();

        assertThat(fired).containsExactly("no");
    }

    /**
     * A confirm carries no continuation: its two branches are the whole of what follows either decision. A ref written
     * after the step is dead, and this is the assertion that says so rather than leaving an operator to discover it.
     */
    @Test
    void aRefWrittenAfterAConfirmStepNeverRunsOnEitherDecision() {
        clickWithLeft(
                """
                [ { do = "confirm:drop", title = "sure", yes = ["yes"], no = ["no"] }, "three" ]
                """,
                listener(null, true));

        java.util.Objects.requireNonNull(confirm.onYes).run();

        assertThat(fired).containsExactly("yes");
    }

    /** An engine wired without a confirm opener declines rather than accepting: a missing gate is not an open one. */
    @Test
    void anEngineWithNoConfirmOpenerTakesTheNoBranch() {
        clickWithLeft(
                """
                [ { do = "confirm:drop", title = "sure", yes = ["yes"], no = ["no"] } ]
                """,
                listener(null, false));

        assertThat(fired).containsExactly("no");
    }

    // -- where the split is not supported -------------------------------------------------------------------

    /**
     * Continuation awareness is only on the success path. A step inside a block's deny list cannot split a chain the
     * engine has no way to suspend, so it is skipped rather than half-run, and the refs beside it still run.
     */
    @Test
    void anInputStepInsideADenyListIsSkippedRatherThanSplittingIt() {
        clickWithLeft(
                """
                {
                  click = ["one"]
                  requirements = ["nobody-registered-this"]
                  deny = [ { do = "input:name", prompt = "type-a-name" }, "two" ]
                }
                """,
                listener(prompt, false));

        assertThat(prompt.keys).as("the deny list opens no prompt").isEmpty();
        assertThat(fired).contains("two");
        assertThat(fired).doesNotContain("one");
    }
}

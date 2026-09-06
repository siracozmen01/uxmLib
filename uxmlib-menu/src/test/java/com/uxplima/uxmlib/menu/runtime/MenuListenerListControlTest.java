package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.ActionRegistry;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.eval.PageRequest;
import com.uxplima.uxmlib.menu.eval.PagedResult;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.ListControlSyntax.SortDirection;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Sorting, filtering and searching a list whose source answers one page at a time. The three controls a click action
 * reaches through {@code ctx.control()} all end in the same re-query a page flip runs, and each is meant to start it
 * from page zero: a viewer who sorts on page four and lands on page four of a differently ordered corpus has been
 * moved somewhere nobody asked for.
 *
 * <p>Every control test in this module so far handed the action a mocked {@code MenuControl}, so what the engine's own
 * implementation does with the holder, the query state and the prompt had never run.
 */
class MenuListenerListControlTest {

    /** A catalogue that hands every key straight back, so a rendered name is readable in an assertion. */
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

    /** Queues the off-thread hop instead of taking it, so a query can be seen while it is still in flight. */
    private static final class QueueingAsync extends SameThreadScheduler {

        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public TaskHandle async(Runnable task) {
            queued.add(task);
            return FINISHED;
        }

        void drain() {
            List<Runnable> due = List.copyOf(queued);
            queued.clear();
            due.forEach(Runnable::run);
        }
    }

    /** Holds the last prompt the engine opened, so a submit or a cancel can be answered by hand. */
    private static final class RecordingPrompt implements MenuTextPrompt {

        private int opened;

        private @Nullable String key;

        private @Nullable Consumer<String> onSubmit;

        private @Nullable Runnable onCancel;

        @Override
        public void prompt(
                Player viewer,
                String key,
                Component prompt,
                @Nullable String initialText,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            opened++;
            this.key = key;
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }
    }

    private static final List<String> CORPUS = List.of("a", "b", "c", "d", "e");

    private static final String SPEC =
            """
            rows = 3
            items {
              row {
                slots = [0, 1]
                list { source = warps, sorts = ["name", "date", "owner"], template { material = PAPER, name = "warp" } }
              }
              sortNext { slot = 6, material = HOPPER, name = "sort", click { left = ["sort-next"] } }
              filter { slot = 5, material = HOPPER, name = "filter", click { left = ["filter"] } }
              search { slot = 4, material = HOPPER, name = "search", click { left = ["search"] } }
              reset { slot = 3, material = HOPPER, name = "reset", click { left = ["reset"] } }
              sortPrev { slot = 2, material = HOPPER, name = "back", click { left = ["sort-previous"] } }
              sortReset { slot = 7, material = HOPPER, name = "plain", click { left = ["sort-reset"] } }
              next { slot = 8, material = ARROW, name = "next", type = next }
            }
            """;

    private final List<PageRequest> asked = new ArrayList<>();

    private QueueingAsync scheduler;

    private PagedListSourceRegistry paged;

    private ListSourceRegistry plain;

    private ActionRegistry actions;

    private RecordingPrompt prompt;

    private Menus menus;

    private MenuListener listener;

    private org.mockbukkit.mockbukkit.entity.PlayerMock viewer;

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new QueueingAsync();
        paged = new PagedListSourceRegistry();
        plain = new ListSourceRegistry();
        actions = new ActionRegistry();
        actions.register("sort-next", ctx -> ctx.control().sortList("warps", SortDirection.NEXT));
        actions.register("sort-previous", ctx -> ctx.control().sortList("warps", SortDirection.PREVIOUS));
        actions.register("sort-reset", ctx -> ctx.control().sortList("warps", SortDirection.RESET));
        actions.register("filter", ctx -> ctx.control().filterList("warps", "owner", "sirac"));
        actions.register("search", ctx -> ctx.control().searchList("warps", "owner"));
        actions.register("reset", ctx -> ctx.control().resetPagination());
        actions.register("elsewhere", ctx -> ctx.control().sortList("kits", SortDirection.NEXT));
        actions.register("search-elsewhere", ctx -> ctx.control().searchList("kits", "owner"));
        prompt = new RecordingPrompt();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
        menus = new Menus(
                renderer,
                scheduler,
                plain,
                null,
                null,
                null,
                null,
                com.uxplima.uxmlib.bedrock.BedrockDetector.NONE,
                com.uxplima.uxmlib.bedrock.BedrockScreen.NONE,
                paged);
        listener = new MenuListener(
                renderer,
                actions,
                new ConditionRegistry(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                () -> 1_000_000L,
                paged,
                prompt,
                null);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A source that answers one page of the corpus and records every request it was handed. */
    private void registerCorpusSource() {
        paged.register("warps", (ctx, request) -> {
            asked.add(request);
            int from = Math.min(request.page() * request.size(), CORPUS.size());
            int to = Math.min(from + request.size(), CORPUS.size());
            return PagedResult.of(CORPUS.subList(from, to), CORPUS.size());
        });
    }

    private void open(String hocon) {
        menus.registerSpec("menu", new MenuSpecLoader().parse(hocon));
        menus.open(viewer, "menu", null);
        scheduler.drain();
    }

    private void open() {
        open(SPEC);
    }

    private MenuHolder holder() {
        Object holder = viewer.getOpenInventory().getTopInventory().getHolder();
        assertThat(holder).isInstanceOf(MenuHolder.class);
        return (MenuHolder) holder;
    }

    private void click(int rawSlot) {
        InventoryView view = viewer.getOpenInventory();
        listener.onClick(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private void clickSort() {
        click(6);
        scheduler.drain();
    }

    private void clickSortPrevious() {
        click(2);
        scheduler.drain();
    }

    private void clickSortReset() {
        click(7);
        scheduler.drain();
    }

    private void clickFilter() {
        click(5);
        scheduler.drain();
    }

    private void clickSearch() {
        click(4);
        scheduler.drain();
    }

    private void clickResetPagination() {
        click(3);
        scheduler.drain();
    }

    private void clickNextPage() {
        click(8);
        scheduler.drain();
    }

    private PageRequest lastRequest() {
        assertThat(asked).isNotEmpty();
        return asked.get(asked.size() - 1);
    }

    // -- sorting -----------------------------------------------------------------------------------------------

    /** The sort the file declared first is the one the open queries with, so a sort control has something to advance. */
    @Test
    void theOpenQueriesWithTheFirstDeclaredSort() {
        registerCorpusSource();
        open();

        assertThat(lastRequest().sort()).isEqualTo("name");
    }

    /** Advancing the sort re-queries with the next declared one, and from page zero. */
    @Test
    void sortingAdvancesToTheNextDeclaredSortAndReturnsToPageZero() {
        registerCorpusSource();
        open();
        clickNextPage();
        assertThat(lastRequest().page()).isEqualTo(1);

        clickSort();

        assertThat(lastRequest().sort()).isEqualTo("date");
        assertThat(lastRequest().page()).isZero();
        assertThat(holder().ctx().page()).isZero();
    }

    /** The list wraps back round its declared sorts, so a viewer clicking one button can reach all of them. */
    @Test
    void sortingWrapsRoundTheDeclaredSorts() {
        registerCorpusSource();
        open();

        clickSort();
        assertThat(lastRequest().sort()).isEqualTo("date");

        clickSort();
        assertThat(lastRequest().sort()).isEqualTo("owner");

        clickSort();
        assertThat(lastRequest().sort())
                .as("past the last declared sort it comes round to the first")
                .isEqualTo("name");
    }

    /**
     * The other direction, which needs three declared sorts to exist at all: over two, stepping back and stepping on
     * land in the same place, and a menu file that binds both to different buttons would look correct either way.
     */
    @Test
    void steppingBackThroughTheSortsIsNotTheSameAsSteppingOn() {
        registerCorpusSource();
        open();

        clickSortPrevious();

        assertThat(lastRequest().sort())
                .as("back from the first declared sort is the last one")
                .isEqualTo("owner");
    }

    /** A reset returns to the first declared sort from wherever the viewer had got to. */
    @Test
    void resettingTheSortReturnsToTheFirstDeclaredOne() {
        registerCorpusSource();
        open();
        clickSort();
        assertThat(lastRequest().sort()).isEqualTo("date");

        clickSortReset();

        assertThat(lastRequest().sort()).as("a reset is not one more step").isEqualTo("name");

        clickSort();
        clickSort();
        assertThat(lastRequest().sort()).isEqualTo("owner");
        clickSortReset();
        assertThat(lastRequest().sort())
                .as("and it comes back from the last one too")
                .isEqualTo("name");
    }

    // -- filtering ---------------------------------------------------------------------------------------------

    /** A filter reaches the source as part of the request, and takes the viewer back to the first page of it. */
    @Test
    void filteringCarriesTheFilterIntoTheRequestAtPageZero() {
        registerCorpusSource();
        open();
        clickNextPage();

        clickFilter();

        assertThat(lastRequest().filters()).containsEntry("owner", "sirac");
        assertThat(lastRequest().page()).isZero();
    }

    // -- searching ---------------------------------------------------------------------------------------------

    /** A search asks the viewer for a line, and the line they type becomes that key's filter. */
    @Test
    void theLineTypedIntoASearchBecomesThatKeysFilter() {
        registerCorpusSource();
        open();
        int before = asked.size();

        clickSearch();

        assertThat(prompt.opened).isEqualTo(1);
        assertThat(prompt.key).isEqualTo("owner");
        assertThat(asked).as("the prompt is open, nothing has been asked yet").hasSize(before);

        Objects.requireNonNull(prompt.onSubmit, "onSubmit").accept("sirac");
        scheduler.drain();

        assertThat(lastRequest().filters()).containsEntry("owner", "sirac");
        assertThat(lastRequest().page()).isZero();
    }

    /** A viewer who changes their mind changes nothing: a cancelled search leaves the list as it was. */
    @Test
    void cancellingASearchLeavesTheListAsItWas() {
        registerCorpusSource();
        open();
        clickSearch();
        int before = asked.size();

        Objects.requireNonNull(prompt.onCancel, "onCancel").run();
        scheduler.drain();

        assertThat(asked).hasSize(before);
    }

    /** An engine wired without a prompt cannot ask, so it says so in the log rather than failing the click. */
    @Test
    void anEngineWithNoPromptLogsRatherThanFailingTheClick() {
        registerCorpusSource();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
        listener = new MenuListener(
                renderer,
                actions,
                new ConditionRegistry(),
                scheduler,
                plugin,
                null,
                null,
                null,
                0L,
                () -> 1_000_000L,
                paged,
                null,
                null);
        open();
        int before = asked.size();

        List<java.util.logging.LogRecord> logged = logsOf(this::clickSearch);

        assertThat(prompt.opened).isZero();
        assertThat(asked).hasSize(before);
        assertThat(logged)
                .as("a click that can never open anything has to say why somewhere")
                .extracting(java.util.logging.LogRecord::getMessage)
                .anySatisfy(message -> assertThat(message).contains("list_search_unavailable"));
    }

    // -- what a control refuses to do --------------------------------------------------------------------------

    /** A control naming a list the menu does not carry is a no-op: an operator typo cannot crash a click. */
    @Test
    void aControlNamingAListTheMenuDoesNotCarryAsksNothing() {
        registerCorpusSource();
        open(SPEC.replace("[\"sort-next\"]", "[\"elsewhere\"]"));
        int before = asked.size();

        clickSort();

        assertThat(asked).hasSize(before);
    }

    /** A plain in-memory list has no source to sort at, so the control drops out rather than querying nothing. */
    @Test
    void aControlOnAPlainListAsksNothing() {
        registerCorpusSource();
        plain.register("warps", ctx -> CORPUS);
        open();
        int before = asked.size();

        clickSort();

        assertThat(asked).hasSize(before);
    }

    /** The controls share the flip's in-flight guard, so a click during a query is dropped rather than queued. */
    @Test
    void aControlWhileAQueryIsInFlightIsDropped() {
        registerCorpusSource();
        open();
        int before = asked.size();

        click(8);
        clickSort();
        scheduler.drain();

        assertThat(asked).hasSize(before + 1);
    }

    /** Resetting the pagination takes the viewer back to the first page through the same query the arrows run. */
    @Test
    void resettingThePaginationGoesBackToTheFirstPage() {
        registerCorpusSource();
        open();
        clickNextPage();
        assertThat(holder().ctx().page()).isEqualTo(1);

        clickResetPagination();

        assertThat(lastRequest().page()).isZero();
        assertThat(holder().ctx().page()).isZero();
    }

    /**
     * A control that names a list the menu does not carry is a file mistake, and the only place it can be seen is the
     * log: nothing is queried, nothing changes on screen, and the click looks the same as one that worked.
     */
    @Test
    void aControlNamingAnUnknownListSaysSoInTheLog() {
        registerCorpusSource();
        open(SPEC.replace("[\"sort-next\"]", "[\"elsewhere\"]"));

        List<java.util.logging.LogRecord> logged = logsOf(this::clickSort);

        assertThat(logged).extracting(java.util.logging.LogRecord::getMessage).anySatisfy(message -> assertThat(message)
                .contains("list_control_unknown_list", "kits"));
    }

    /** The same for a search: an unknown list opens no prompt, and says why in the log. */
    @Test
    void aSearchNamingAnUnknownListOpensNoPromptAndSaysSoInTheLog() {
        registerCorpusSource();
        open(SPEC.replace("[\"search\"]", "[\"search-elsewhere\"]"));

        List<java.util.logging.LogRecord> logged = logsOf(this::clickSearch);

        assertThat(prompt.opened)
                .as("a prompt for a list that is not there would ask for a line nobody can use")
                .isZero();
        assertThat(logged).extracting(java.util.logging.LogRecord::getMessage).anySatisfy(message -> assertThat(message)
                .contains("list_control_unknown_list"));
    }

    /**
     * A viewer who left between the click and the hop gets no prompt. The control hops to the viewer's entity thread
     * before it opens anything, and on a real server that hop is a later tick; only a scheduler that defers it can
     * show that the guard on the far side of the hop does any work.
     */
    @Test
    void aSearchByAViewerWhoLeftBeforeTheHopLandsOpensNoPrompt() {
        registerCorpusSource();
        open();
        DeferringEntity deferring = new DeferringEntity();
        listener = new MenuListener(
                new MenuRenderer(
                        new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()),
                        new ConditionRegistry()),
                actions,
                new ConditionRegistry(),
                deferring,
                plugin,
                null,
                null,
                null,
                0L,
                () -> 1_000_000L,
                paged,
                prompt,
                null);

        click(4);
        assertThat(prompt.opened)
                .as("the control has not reached the entity thread yet")
                .isZero();
        viewer.disconnect();
        deferring.drain();

        assertThat(prompt.opened).isZero();
    }

    /** Queues the hop onto the viewer's thread instead of taking it, so a viewer can leave while it is in the air. */
    private static final class DeferringEntity extends SameThreadScheduler {

        private final List<Runnable> queued = new ArrayList<>();

        @Override
        public TaskHandle entity(org.bukkit.entity.Entity entity, Runnable task) {
            queued.add(task);
            return FINISHED;
        }

        void drain() {
            List<Runnable> due = List.copyOf(queued);
            queued.clear();
            due.forEach(Runnable::run);
        }
    }

    /**
     * A sort chosen on a later page comes back to the first one. The rows the new sort puts on page three have
     * nothing to do with the rows the old sort had there, so staying on the page number would show the viewer an
     * arbitrary slice of a list they just reordered.
     */
    @Test
    void aSortChosenOnALaterPageComesBackToTheFirstPage() {
        registerCorpusSource();
        open();
        clickNextPage();
        assertThat(lastRequest().page())
                .as("the arrow moved the list off page one")
                .isEqualTo(1);
        asked.clear();

        clickSort();

        assertThat(lastRequest().page()).isZero();
        assertThat(holder().ctx().page()).isZero();
    }

    /** Collects what the engine logs while {@code body} runs, so a no-op that only reports itself can be read back. */
    private static List<java.util.logging.LogRecord> logsOf(Runnable body) {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MenuListener.class.getName());
        List<java.util.logging.LogRecord> records = new ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            body.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }
}

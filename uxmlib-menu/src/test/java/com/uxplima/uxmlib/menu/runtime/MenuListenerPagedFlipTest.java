package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
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
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Paging a list whose source answers for one page at a time. A plain list is re-sliced from a cache the holder
 * already has, synchronously; a paged list is not, so a page flip leaves the entity thread, asks the source, and
 * comes back to commit. Every listener test so far handed the engine an empty paged registry, so none of that ran.
 *
 * <p>What makes it worth its own file is the flag. The flip sets {@code pagedFlipInFlight} on the holder and clears
 * it on the way back, and every way the query can end has to clear it: a page that lands, a source that throws, a
 * source that hands back nothing, a window closed while the query ran. A path that forgets leaves the viewer's
 * arrows dead for the rest of their session, with no error anywhere to say why.
 */
class MenuListenerPagedFlipTest {

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

    private static final List<String> CORPUS = List.of("a", "b", "c", "d", "e");

    private static final String SPEC =
            """
            rows = 3
            items {
              row { slots = [0, 1], list { source = warps, template { material = PAPER, name = "warp" } } }
              next { slot = 8, material = ARROW, name = "next", type = next }
              prev { slot = 7, material = ARROW, name = "prev", type = previous }
            }
            """;

    private final List<PageRequest> asked = new ArrayList<>();

    private QueueingAsync scheduler;

    private PagedListSourceRegistry paged;

    private ListSourceRegistry plain;

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new QueueingAsync();
        paged = new PagedListSourceRegistry();
        plain = new ListSourceRegistry();
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
                new ActionRegistry(),
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

    private void open() {
        menus.registerSpec("menu", new MenuSpecLoader().parse(SPEC));
        menus.open(viewer, "menu", null);
        scheduler.drain();
    }

    private Inventory top() {
        return viewer.getOpenInventory().getTopInventory();
    }

    private MenuHolder holder() {
        Object holder = top().getHolder();
        assertThat(holder).isInstanceOf(MenuHolder.class);
        return (MenuHolder) holder;
    }

    private void click(int rawSlot) {
        InventoryView view = viewer.getOpenInventory();
        listener.onClick(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    private void clickNext() {
        click(8);
    }

    private void clickPrevious() {
        click(7);
    }

    private List<Object> rowsOnScreen() {
        return rowsOf("warps");
    }

    /** The rows the holder currently holds for one list, copied out so an assertion sees a plain element type. */
    private List<Object> rowsOf(String listId) {
        return List.copyOf(holder().resolvedLists().getOrDefault(listId, List.of()));
    }

    // -- the flip itself ---------------------------------------------------------------------------------------

    /** The source is asked for the page the arrow points at, and asked once, with the size the layout gives it. */
    @Test
    void aNextClickAsksTheSourceForThatPageAtTheLayoutsPageSize() {
        registerCorpusSource();
        open();
        asked.clear();

        clickNext();
        scheduler.drain();

        assertThat(asked).hasSize(1);
        assertThat(asked.get(0).page()).isEqualTo(1);
        assertThat(asked.get(0).size()).isEqualTo(2);
    }

    /** The window the viewer is looking at is repainted in place: no second window, and the new rows are on it. */
    @Test
    void theFetchedPageLandsOnTheSameWindow() {
        registerCorpusSource();
        open();
        Inventory before = top();

        clickNext();
        scheduler.drain();

        assertThat(top()).isSameAs(before);
        assertThat(rowsOnScreen()).containsExactly("c", "d");
        assertThat(holder().ctx().page()).isEqualTo(1);
    }

    /** A previous click walks back the same way, so the two arrows are not one tested and one assumed. */
    @Test
    void aPreviousClickWalksBackAPage() {
        registerCorpusSource();
        open();
        clickNext();
        scheduler.drain();

        clickPrevious();
        scheduler.drain();

        assertThat(rowsOnScreen()).containsExactly("a", "b");
        assertThat(holder().ctx().page()).isZero();
    }

    /**
     * A flip that cannot move issues no query at all. Asking the source for a page that does not exist would put a
     * database read behind an arrow that visibly does nothing.
     */
    @Test
    void anArrowAtTheEndOfTheCorpusAsksNothing() {
        registerCorpusSource();
        open();
        clickNext();
        scheduler.drain();
        clickNext();
        scheduler.drain();
        asked.clear();

        clickNext();
        scheduler.drain();

        assertThat(holder().ctx().page()).isEqualTo(2);
        assertThat(asked).isEmpty();
    }

    /** The same at the other end: a previous click on page zero is a no-op, not a query for page minus one. */
    @Test
    void anArrowAtTheStartOfTheCorpusAsksNothing() {
        registerCorpusSource();
        open();
        asked.clear();

        clickPrevious();
        scheduler.drain();

        assertThat(asked).isEmpty();
        assertThat(holder().ctx().page()).isZero();
    }

    // -- the in-flight flag ------------------------------------------------------------------------------------

    /**
     * A mashed arrow issues one query, not one per click. Queueing them would let an earlier page land after a later
     * one and leave the viewer on a page they already left.
     */
    @Test
    void aSecondFlipWhileOneIsInFlightIsDroppedRatherThanQueued() {
        registerCorpusSource();
        open();
        asked.clear();

        clickNext();
        clickNext();
        clickNext();
        scheduler.drain();

        assertThat(asked).hasSize(1);
    }

    /** Once the page lands the flag is clear again, so the next click is a fresh query rather than a dead arrow. */
    @Test
    void theArrowWorksAgainOnceThePageHasLanded() {
        registerCorpusSource();
        open();
        clickNext();
        scheduler.drain();
        asked.clear();

        clickNext();
        scheduler.drain();

        assertThat(asked).hasSize(1);
        assertThat(asked.get(0).page()).isEqualTo(2);
    }

    /**
     * A source that throws must not wedge the arrows. The page on screen stays, and the next click issues a query:
     * the alternative is a viewer whose menu silently stops paging until they log out.
     */
    @Test
    void aSourceThatThrowsLeavesThePageUpAndFreesTheArrows() {
        List<PageRequest> seen = new ArrayList<>();
        paged.register("warps", (ctx, request) -> {
            seen.add(request);
            if (seen.size() == 2) {
                throw new IllegalStateException("the database is down");
            }
            int from = Math.min(request.page() * request.size(), CORPUS.size());
            int to = Math.min(from + request.size(), CORPUS.size());
            return PagedResult.of(CORPUS.subList(from, to), CORPUS.size());
        });
        open();

        assertThatCode(() -> {
                    clickNext();
                    scheduler.drain();
                })
                .doesNotThrowAnyException();

        assertThat(rowsOnScreen()).containsExactly("a", "b");
        assertThat(holder().ctx().page()).isZero();

        clickNext();
        scheduler.drain();

        assertThat(seen).hasSize(3);
        assertThat(rowsOnScreen()).containsExactly("c", "d");
    }

    /** A source handing back nothing is a source failure, not an empty page, and it recovers on the same terms. */
    @Test
    void aSourceThatHandsBackNothingIsTreatedAsAFailureRatherThanAnEmptyPage() {
        paged.register(
                "warps",
                (ctx, request) -> request.page() == 0 ? PagedResult.of(CORPUS.subList(0, 2), CORPUS.size()) : null);
        open();

        assertThatCode(() -> {
                    clickNext();
                    scheduler.drain();
                })
                .doesNotThrowAnyException();

        assertThat(rowsOnScreen()).containsExactly("a", "b");
        assertThat(holder().ctx().page()).isZero();
    }

    /**
     * The viewer can close the window while the query is running. The flag is cleared before the window is read, so
     * the close is a clean return rather than a repaint of a holder nobody is looking at.
     */
    @Test
    void aWindowClosedWhileTheQueryRanIsNotRepainted() {
        registerCorpusSource();
        open();
        MenuHolder open = holder();
        clickNext();
        viewer.closeInventory();

        assertThatCode(scheduler::drain).doesNotThrowAnyException();

        assertThat(open.pagedFlipInFlight()).isFalse();
        assertThat(open.ctx().page()).isZero();
    }

    // -- what the flip commits ---------------------------------------------------------------------------------

    /** The corpus total the source reports is what the next clamp reads, so a shrinking corpus stops paging early. */
    @Test
    void theTotalTheSourceReportsIsWhatTheNextClampReads() {
        paged.register("warps", (ctx, request) -> {
            asked.add(request);
            int from = Math.min(request.page() * request.size(), CORPUS.size());
            int to = Math.min(from + request.size(), CORPUS.size());
            return PagedResult.of(CORPUS.subList(from, to), 4);
        });
        open();
        clickNext();
        scheduler.drain();
        asked.clear();

        clickNext();
        scheduler.drain();

        assertThat(asked)
                .as("a total of four over pages of two leaves nothing past page one")
                .isEmpty();
    }

    /** A plain list sharing the menu keeps its own cached rows: only the flipped list's rows are swapped. */
    @Test
    void aPlainListSharingTheMenuKeepsItsRowsThroughTheFlip() {
        registerCorpusSource();
        plain.register("kits", ctx -> List.of("stone", "iron"));
        menus.registerSpec(
                "menu",
                new MenuSpecLoader()
                        .parse(
                                """
                                rows = 3
                                items {
                                  row { slots = [0, 1], list { source = warps, template { material = PAPER, name = "warp" } } }
                                  kits { slots = [3, 4], list { source = kits, template { material = CHEST, name = "kit" } } }
                                  next { slot = 8, material = ARROW, name = "next", type = next }
                                }
                                """));
        menus.open(viewer, "menu", null);
        scheduler.drain();

        clickNext();
        scheduler.drain();

        assertThat(rowsOf("kits")).containsExactly("stone", "iron");
        assertThat(rowsOnScreen()).containsExactly("c", "d");
    }

    /** A menu whose list is a plain in-memory source never reaches the paged registry at all. */
    @Test
    void aPlainListMenuAsksNoPagedSource() {
        registerCorpusSource();
        plain.register("kits", ctx -> List.of("stone", "iron", "gold"));
        menus.registerSpec(
                "menu",
                new MenuSpecLoader()
                        .parse(
                                """
                                rows = 3
                                items {
                                  kits { slots = [0, 1], list { source = kits, template { material = CHEST, name = "kit" } } }
                                  next { slot = 8, material = ARROW, name = "next", type = next }
                                }
                                """));
        menus.open(viewer, "menu", null);
        scheduler.drain();
        asked.clear();

        clickNext();
        scheduler.drain();

        assertThat(asked).isEmpty();
        assertThat(holder().ctx().page()).isEqualTo(1);
    }

    /** The page that lands is drawn: a shorter last page leaves the slots it does not fill empty. */
    @Test
    void aShorterLastPageLeavesTheSlotsItDoesNotFillEmpty() {
        registerCorpusSource();
        open();
        clickNext();
        scheduler.drain();
        clickNext();
        scheduler.drain();

        assertThat(top().getItem(0)).isNotNull();
        assertThat(top().getItem(1)).isNull();
        assertThat(rowsOnScreen()).containsExactly("e");
    }

    /** The material a page draws is the template's, so a landed page is a real render and not a state update. */
    @Test
    void theLandedPageIsRenderedAndNotJustRecorded() {
        registerCorpusSource();
        open();

        clickNext();
        scheduler.drain();

        assertThat(top().getItem(0)).isNotNull();
        assertThat(top().getItem(0).getType()).isEqualTo(Material.PAPER);
    }

    /**
     * A menu pairing two lists pages the one drawn nearest the start of the window, on every run. The engine used to
     * take whichever list its item map handed over first, and that map comes from the config library rather than from
     * the file: the same menu paged the warps on one server start and the kits on the next, with nothing to say why.
     */
    @Test
    void theArrowsDriveTheListDrawnNearestTheStartOfTheWindow() {
        List<PageRequest> kitPages = new ArrayList<>();
        registerCorpusSource();
        paged.register("kits", (ctx, request) -> {
            kitPages.add(request);
            return PagedResult.of(List.of("stone", "iron"), 4);
        });
        menus.registerSpec(
                "menu",
                new MenuSpecLoader()
                        .parse(
                                """
                                rows = 3
                                items {
                                  kits { slots = [3, 4], list { source = kits, template { material = CHEST, name = "kit" } } }
                                  row { slots = [0, 1], list { source = warps, template { material = PAPER, name = "warp" } } }
                                  next { slot = 8, material = ARROW, name = "next", type = next }
                                }
                                """));
        menus.open(viewer, "menu", null);
        scheduler.drain();
        asked.clear();
        kitPages.clear();

        clickNext();
        scheduler.drain();

        assertThat(asked)
                .as("the warps are drawn at slot zero, so the arrow moves them")
                .hasSize(1);
        assertThat(kitPages)
                .as("the kits are drawn later in the window, so the arrow leaves them alone")
                .isEmpty();
        assertThat(rowsOnScreen()).containsExactly("c", "d");
    }

    /** Collects what the engine logs while {@code body} runs, so a failure's own report can be read back. */
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

    /**
     * A failed flip is silent to the viewer, so the log is the only place it exists. It names the list and the page,
     * which is what an operator has to go on when a player says the arrows stopped working.
     */
    @Test
    void aFailedQueryNamesTheListAndThePageInTheLog() {
        paged.register("warps", (ctx, request) -> {
            if (request.page() == 0) {
                return PagedResult.of(CORPUS.subList(0, 2), CORPUS.size());
            }
            throw new IllegalStateException("the database is down");
        });
        open();

        List<java.util.logging.LogRecord> logged = logsOf(() -> {
            clickNext();
            scheduler.drain();
        });

        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getMessage()).contains("warps").contains("page=1");
    }

    /**
     * A source that hands back nothing is named for what it is. Without the check the same flip fails as a null
     * pointer thrown somewhere inside the page assembly, which says nothing about whose source returned it.
     */
    @Test
    void aNullPageIsNamedRatherThanArrivingAsANullPointer() {
        paged.register(
                "warps",
                (ctx, request) -> request.page() == 0 ? PagedResult.of(CORPUS.subList(0, 2), CORPUS.size()) : null);
        open();

        List<java.util.logging.LogRecord> logged = logsOf(() -> {
            clickNext();
            scheduler.drain();
        });

        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getThrown())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("warps");
    }
}

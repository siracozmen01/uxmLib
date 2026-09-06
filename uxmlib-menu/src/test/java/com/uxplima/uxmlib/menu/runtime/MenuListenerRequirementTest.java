package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * What a click does once its requirement block has an answer. The loader tests prove a file parses into the right
 * shape; these prove the router walks that shape, which is a different question: a block that parses perfectly can
 * still be evaluated in the wrong order, ask one condition too many, or run a deny list twice.
 *
 * <p>Two conditions carry the whole suite. {@code yes} always holds and {@code no} never does, and both record that
 * they were asked, so a test can assert on what was <em>not</em> evaluated as easily as on what ran. That matters for
 * {@code stop_at_success}, whose entire promise is that a later requirement is never reached.
 */
class MenuListenerRequirementTest {

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

    private final List<String> fired = new ArrayList<>();

    private final List<String> asked = new ArrayList<>();

    private Menus menus;

    private MenuListener listener;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();

        ActionRegistry actions = new ActionRegistry();
        for (String id : List.of("main", "otherwise", "blockDeny", "reqOk", "reqNo", "branchA", "branchB", "tail")) {
            actions.register(id, ctx -> fired.add(id));
        }

        ConditionRegistry conditions = new ConditionRegistry();
        conditions.register("yes", (ctx, args) -> {
            asked.add("yes");
            return true;
        });
        conditions.register("no", (ctx, args) -> {
            asked.add("no");
            return false;
        });

        menus = new Menus(renderer(), new SameThreadScheduler(), new ListSourceRegistry());
        listener = new MenuListener(
                renderer(),
                actions,
                conditions,
                new SameThreadScheduler(),
                plugin,
                null,
                null,
                null,
                0L,
                () -> 1_000_000L,
                new PagedListSourceRegistry(),
                null,
                null);
    }

    private static MenuRenderer renderer() {
        return new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Wrap {@code gesture} in the one-item menu every test here clicks, register it, and open it. */
    private void openWithLeft(String gesture) {
        String hocon = "rows = 1\nitems { a { slot = 0, material = DIAMOND, click { left " + gesture + " } } }";
        menus.registerSpec("menu", new MenuSpecLoader().parse(hocon));
        menus.open(viewer, "menu", null);
    }

    private void leftClick() {
        InventoryView view = viewer.getOpenInventory();
        listener.onClick(new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    // -- the block's own pass rule ----------------------------------------------------------------------------

    @Test
    void everyMandatoryRequirementMustPassBeforeTheActionsRun() {
        openWithLeft("{ click = [\"main\"], requirements = [\"yes\", \"yes\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("main");
    }

    @Test
    void oneFailedMandatoryRequirementStopsTheActionsAndRunsTheBlockDeny() {
        openWithLeft("{ click = [\"main\"], requirements = [\"yes\", \"no\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("blockDeny");
    }

    /**
     * The deny list belongs to the block, not to the gesture. A block with no requirements at all passes, so its deny
     * has nothing to answer and must stay quiet: an operator who writes a deny arm and no gate has written a bug, and
     * running it every click would hide that from them behind a message that always appears.
     */
    @Test
    void aBlockWithNoRequirementsPassesAndItsDenyDoesNotRun() {
        openWithLeft("{ click = [\"main\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("main");
    }

    @Test
    void anInvertedRequirementPassesExactlyWhenItsConditionDoesNot() {
        openWithLeft("{ click = [\"main\"], requirements = [\"!no\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("main");
    }

    /** A condition nobody registered is a wiring gap. It denies, because the alternative is granting by accident. */
    @Test
    void aConditionNothingWasRegisteredUnderFailsClosed() {
        openWithLeft("{ click = [\"main\"], requirements = [\"nobody-registered-this\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("blockDeny");
        assertThat(asked).as("no registered condition was reached").isEmpty();
    }

    // -- optional requirements and the minimum ----------------------------------------------------------------

    /**
     * An optional requirement cannot fail the block, yet it is still a requirement: its own deny arm is the point of
     * writing it, so that arm runs while the click goes through.
     */
    @Test
    void anOptionalRequirementFailingLeavesTheBlockPassingButStillRunsItsOwnDeny() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = ["yes", { require = "no", optional = true, deny = ["reqNo"] }]
                  deny = ["blockDeny"]
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("reqNo", "main");
    }

    /** With a minimum the count is over every requirement, so an optional pass can carry a mandatory failure. */
    @Test
    void aMinimumCountsTheOptionalRequirementsToo() {
        openWithLeft("""
                {
                  click = ["main"]
                  minimum = 1
                  requirements = ["no", { require = "yes", optional = true }]
                  deny = ["blockDeny"]
                }
                """);
        leftClick();
        assertThat(fired).as("one of two passed, and one was asked for").containsExactly("main");
    }

    @Test
    void aMinimumTheBlockDoesNotReachDeniesEvenThoughOneRequirementPassed() {
        openWithLeft("{ click = [\"main\"], minimum = 2, requirements = [\"yes\", \"no\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("blockDeny");
    }

    /**
     * A minimum larger than the block is capped at the block's size, so an over-large N means "all of them" rather
     * than "never". The alternative reading turns an operator's typo into a button that can never be clicked and
     * never says why, which is the harder mistake to find of the two.
     */
    @Test
    void aMinimumLargerThanTheRequirementListMeansAllOfThem() {
        openWithLeft("{ click = [\"main\"], minimum = 5, requirements = [\"yes\", \"yes\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("main");
    }

    @Test
    void anOverLargeMinimumStillDeniesWhenOneOfTheRequirementsFails() {
        openWithLeft("{ click = [\"main\"], minimum = 5, requirements = [\"yes\", \"no\"], deny = [\"blockDeny\"] }");
        leftClick();
        assertThat(fired).containsExactly("blockDeny");
    }

    // -- stop_at_success ---------------------------------------------------------------------------------------

    /**
     * The whole promise of {@code stop_at_success} is that a later requirement is not reached. Asserting on the
     * actions alone would not see it: the block passes either way, and only the ask list tells the two apart.
     */
    @Test
    void stopAtSuccessLeavesTheRequirementsPastTheMinimumUnasked() {
        openWithLeft("""
                {
                  click = ["main"]
                  minimum = 1
                  stop_at_success = true
                  requirements = ["yes", { require = "no", deny = ["reqNo"] }]
                }
                """);
        leftClick();
        assertThat(asked).containsExactly("yes");
        assertThat(fired).as("the requirement that was never asked ran no arm").containsExactly("main");
    }

    @Test
    void withoutStopAtSuccessEveryRequirementIsAskedAndEveryArmRuns() {
        openWithLeft("""
                {
                  click = ["main"]
                  minimum = 1
                  requirements = ["yes", { require = "no", deny = ["reqNo"] }]
                }
                """);
        leftClick();
        assertThat(asked).containsExactly("yes", "no");
        assertThat(fired).containsExactly("reqNo", "main");
    }

    /**
     * Without a minimum there is no count to stop at. {@code stop_at_success} on such a block means nothing, and the
     * block must still ask every requirement: an early break would let one pass hide a mandatory failure behind it.
     */
    @Test
    void stopAtSuccessWithNoMinimumStillAsksEveryRequirement() {
        openWithLeft("""
                {
                  click = ["main"]
                  stop_at_success = true
                  requirements = ["yes", "no"]
                  deny = ["blockDeny"]
                }
                """);
        leftClick();
        assertThat(asked).containsExactly("yes", "no");
        assertThat(fired).containsExactly("blockDeny");
    }

    // -- per-requirement arms ----------------------------------------------------------------------------------

    @Test
    void aPassingRequirementRunsItsOwnSuccessArmAndNotItsDeny() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = [{ require = "yes", success = ["reqOk"], deny = ["reqNo"] }]
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("reqOk", "main");
    }

    /** A per-requirement arm runs as the block is walked, so it lands before the block's own deny, not after it. */
    @Test
    void aPerRequirementDenyRunsBeforeTheBlockDenyItLedTo() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = [{ require = "no", deny = ["reqNo"] }]
                  deny = ["blockDeny"]
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("reqNo", "blockDeny");
    }

    // -- the else ladder ---------------------------------------------------------------------------------------

    @Test
    void aFailedBlockWithAnElseLadderTriesTheLadderInsteadOfItsOwnDeny() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = ["no"]
                  deny = ["blockDeny"]
                  else { requirements = ["yes"], click = ["branchA"] }
                }
                """);
        leftClick();
        assertThat(fired)
                .as("the ladder replaces the block deny rather than running beside it")
                .containsExactly("branchA");
    }

    @Test
    void theFirstSatisfiedBranchWinsAndTheRestOfTheLadderIsNotWalked() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = ["no"]
                  else {
                    requirements = ["yes"]
                    click = ["branchA"]
                    else { requirements = ["yes"], click = ["branchB"] }
                  }
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("branchA");
    }

    @Test
    void aBranchThatFailsHandsOnToTheNextOne() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = ["no"]
                  else {
                    requirements = ["no"]
                    click = ["branchA"]
                    else { requirements = ["yes"], click = ["branchB"] }
                  }
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("branchB");
    }

    /** A terminal else has no requirements, so it always passes: the "otherwise" arm of the ladder. */
    @Test
    void aTerminalElseRunsWhateverTheBranchesAboveItDecided() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = ["no"]
                  else {
                    requirements = ["no"]
                    click = ["branchA"]
                    else { click = ["otherwise"] }
                  }
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("otherwise");
    }

    /** A ladder that runs out with every branch failing falls back on the last branch's own deny, not the block's. */
    @Test
    void aLadderWhereEveryBranchFailsRunsTheLastBranchesDeny() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = ["no"]
                  deny = ["blockDeny"]
                  else {
                    requirements = ["no"]
                    click = ["branchA"]
                    deny = ["tail"]
                  }
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("tail");
    }

    /** A branch is a block: its per-requirement arms fire exactly as a top-level block's do. */
    @Test
    void aBranchesOwnPerRequirementArmsFireWhileTheLadderIsWalked() {
        openWithLeft("""
                {
                  click = ["main"]
                  requirements = ["no"]
                  else {
                    requirements = [{ require = "yes", success = ["reqOk"] }]
                    click = ["branchA"]
                  }
                }
                """);
        leftClick();
        assertThat(fired).containsExactly("reqOk", "branchA");
    }
}

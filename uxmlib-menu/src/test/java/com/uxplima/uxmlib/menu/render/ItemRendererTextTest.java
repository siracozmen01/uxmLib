package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuItemSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The two passes an item's text goes through before it is handed to the catalogue: the {@code %token%}
 * substitution and the {@code {math:}} evaluation. Both are written with a regex matcher, and both replace into a
 * builder, which is a place where a value carrying a dollar sign turns into a capture group if the writer forgot to
 * quote it. Both are fail-soft on the way out, so what a rejected expression leaves behind is worth stating.
 */
class ItemRendererTextTest {

    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text("catalogue(" + key + ")");
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    private final PlaceholderRegistry placeholders = new PlaceholderRegistry();

    private ItemRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        renderer = new ItemRenderer(new PlainText(), Theme::defaults, placeholders);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuItemSpec item(String hocon) {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems { one { slot = 0, " + hocon + " } }");
        return Objects.requireNonNull(spec.items().get("one"));
    }

    /** The rendered display name of an item whose name is {@code raw}, as plain text. */
    private String name(String raw) {
        ItemStack stack =
                renderer.render(item("material = STONE, name = \"" + raw + "\""), MenuContext.of(viewer, null, 0));
        Component displayed = Objects.requireNonNull(stack.getItemMeta()).displayName();
        return displayed == null ? "" : PlainTextComponentSerializer.plainText().serialize(displayed);
    }

    // -- the math pass ----------------------------------------------------------------------------------------

    @Test
    void anExpressionIsReplacedByItsResult() {
        assertThat(name("{math: 2 + 3}")).isEqualTo("5");
    }

    @Test
    void aTokenInsideAnExpressionIsResolvedBeforeTheExpressionIsEvaluated() {
        placeholders.register("coins", ctx -> "50");

        assertThat(name("{math: %coins% * 2}")).isEqualTo("100");
    }

    @Test
    void twoExpressionsOnOneLineAreBothReplaced() {
        assertThat(name("{math: 1 + 1} and {math: 2 + 2}")).isEqualTo("2 and 4");
    }

    @Test
    void theWordsAroundAnExpressionAreKept() {
        assertThat(name("you have {math: 3 * 3} left")).isEqualTo("you have 9 left");
    }

    /**
     * A rejected expression leaves nothing behind rather than the raw text. That is deliberate (a menu showing
     * {@code {math: 2 +}} to a player is worse than a gap) and it is also invisible: the gap it leaves is the same
     * gap an unresolved placeholder leaves, so nothing downstream can tell the two apart.
     */
    @Test
    void anExpressionThatDoesNotParseLeavesAGapAndNotTheExpression() {
        assertThat(name("total: {math: 2 +}")).isEqualTo("total: ");
    }

    /**
     * A token nobody registered resolves to nothing, and nothing is what the math pass then sees. With a
     * multiplication that leaves {@code "* 2"}, which does not parse, so the block is refused and the line shows a
     * gap. This is the safe half of the behaviour and it is the half an author's own test values land on.
     */
    @Test
    void anUnresolvedOperandUnderMostOperatorsLeavesAGap() {
        assertThat(name("total: {math: %missing% * 2}")).isEqualTo("total: ");
    }

    /**
     * The unsafe half, and it is not a gap. Plus and minus are also valid as unary prefixes, so a missing left
     * operand leaves {@code "+ 1"}, which parses, evaluates, and puts the number 1 in front of a player as though
     * the menu meant it. Nothing warns, nothing is blank, and the wrong number is indistinguishable from a right
     * one.
     *
     * <p>The fix is not local: the math pass runs after the token pass on purpose, so that a token holding a
     * {@code {math:}} block of its own is still evaluated, and by the time the expression is seen the fact that an
     * operand went missing has been erased. Reversing the two passes would trade this defect for that one. This
     * test states the behaviour rather than approving it, so that the pipeline rework has a red line to turn green
     * and cannot fix it by accident without noticing.
     */
    @Test
    void anUnresolvedOperandUnderAUnaryCapableOperatorIsShownAsANumberInstead() {
        assertThat(name("total: {math: %missing% + 1}")).isEqualTo("total: 1");
        assertThat(name("total: {math: %missing% - 1}")).isEqualTo("total: -1");
    }

    @Test
    void aLineWithNoExpressionIsHandedOnUnchanged() {
        assertThat(name("nothing to add here")).isEqualTo("nothing to add here");
    }

    // -- the token pass ---------------------------------------------------------------------------------------

    @Test
    void aTokenIsReplacedByItsValue() {
        placeholders.register("who", ctx -> "Sirac");

        assertThat(name("hello %who%")).isEqualTo("hello Sirac");
    }

    @Test
    void aTokenNobodyRegisteredResolvesToNothingRatherThanToItsOwnName() {
        assertThat(name("hello %who%")).isEqualTo("hello ");
    }

    /**
     * Both passes replace into a {@link StringBuilder} through a matcher, where a dollar sign in the replacement is
     * a capture-group reference unless the writer quotes it. A player name is operator-adjacent data and a
     * placeholder may resolve to anything at all, so this pins the quoting rather than trusting it.
     */
    @Test
    void aValueCarryingADollarSignIsTakenLiterallyAndNotAsACaptureGroup() {
        placeholders.register("price", ctx -> "$100");

        assertThat(name("costs %price%")).isEqualTo("costs $100");
    }

    @Test
    void aValueCarryingABackslashIsTakenLiterallyToo() {
        placeholders.register("path", ctx -> "a\\b");

        assertThat(name("path %path%")).isEqualTo("path a\\b");
    }

    @Test
    void aValueCarryingAPercentSignDoesNotOpenASecondToken() {
        placeholders.register("rate", ctx -> "50%");
        placeholders.register("who", ctx -> "Sirac");

        assertThat(name("%rate% of %who%")).isEqualTo("50% of Sirac");
    }
}

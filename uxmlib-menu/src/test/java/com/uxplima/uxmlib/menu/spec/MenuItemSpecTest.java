package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The item record an operator's file turns into, and the ten copy helpers the in-game editor rebuilds it with. Each
 * helper writes one field and carries the other twelve over, and every one of them is the same twelve-argument call
 * with one word changed, which is the shape a copy and paste goes wrong in. The tests below change one field and then
 * insist that nothing else moved, so a helper that hands the wrong word over is a failure rather than a surprise an
 * operator meets later in a window.
 */
class MenuItemSpecTest {

    private static final SlotSet SLOTS = SlotSet.parse(List.of("3"), 54);

    private static final ItemDecor DECOR = new ItemDecor(2, Optional.of(7), true, List.of("HIDE_ATTRIBUTES"));

    private static final ClickSpec CLICK = new ClickSpec(Map.of(ClickKind.LEFT, List.of(Ref.parse("close"))), Map.of());

    /** An item whose every field carries a different value, so a helper that copies the wrong one is visible. */
    private static MenuItemSpec base() {
        return new MenuItemSpec(
                SLOTS,
                4,
                "STONE",
                "name",
                List.of("first", "second"),
                DECOR,
                LoreMode.APPEND,
                RequirementSpec.allOf(List.of(Ref.parse("permission"))),
                CLICK,
                true,
                Optional.empty(),
                ItemType.NEXT,
                Optional.of(new ItemDragSpec(new ItemRuleSpec(List.of("PAPER"), 1, "warp"), true, List.of())));
    }

    @Test
    void withSlotsWritesTheSlotsAndNothingElse() {
        MenuItemSpec moved = base().withSlots(SlotSet.parse(List.of("8"), 54));

        assertThat(moved.slots().slots()).containsExactly(8);
        assertOnlyChanged(moved, "slots");
    }

    @Test
    void withPriorityWritesThePriorityAndNothingElse() {
        MenuItemSpec raised = base().withPriority(9);

        assertThat(raised.priority()).isEqualTo(9);
        assertOnlyChanged(raised, "priority");
    }

    @Test
    void withMaterialWritesTheMaterialAndNothingElse() {
        MenuItemSpec other = base().withMaterial("head:Notch");

        assertThat(other.material()).isEqualTo("head:Notch");
        assertOnlyChanged(other, "material");
    }

    @Test
    void withNameWritesTheNameAndNothingElse() {
        MenuItemSpec renamed = base().withName("other");

        assertThat(renamed.name()).isEqualTo("other");
        assertOnlyChanged(renamed, "name");
    }

    @Test
    void withLoreWritesTheLoreAndNothingElse() {
        MenuItemSpec relabelled = base().withLore(List.of("only"));

        assertThat(relabelled.lore()).containsExactly("only");
        assertOnlyChanged(relabelled, "lore");
    }

    @Test
    void withDecorWritesTheDecorAndNothingElse() {
        ItemDecor plain = new ItemDecor(1, Optional.empty(), false, List.of());

        MenuItemSpec undecorated = base().withDecor(plain);

        assertThat(undecorated.decor()).isEqualTo(plain);
        assertOnlyChanged(undecorated, "decor");
    }

    @Test
    void withLoreModeWritesTheModeAndNothingElse() {
        MenuItemSpec prepending = base().withLoreMode(LoreMode.PREPEND);

        assertThat(prepending.loreMode()).isEqualTo(LoreMode.PREPEND);
        assertOnlyChanged(prepending, "loreMode");
    }

    @Test
    void withTypeWritesThePaginationRoleAndNothingElse() {
        MenuItemSpec previous = base().withType(ItemType.PREVIOUS);

        assertThat(previous.type()).isEqualTo(ItemType.PREVIOUS);
        assertOnlyChanged(previous, "type");
    }

    @Test
    void withViewWritesTheVisibilityGateAndNothingElse() {
        RequirementSpec open = RequirementSpec.allOf(List.of());

        MenuItemSpec visible = base().withView(open);

        assertThat(visible.view()).isEqualTo(open);
        assertOnlyChanged(visible, "view");
    }

    @Test
    void withClickWritesTheClickBlockAndNothingElse() {
        ClickSpec silent = new ClickSpec(Map.of(), Map.of());

        MenuItemSpec quiet = base().withClick(silent);

        assertThat(quiet.click()).isEqualTo(silent);
        assertOnlyChanged(quiet, "click");
    }

    @Test
    void aCopyOfACopyKeepsTheFirstChange() {
        MenuItemSpec twice = base().withName("other").withPriority(9);

        assertThat(twice.name()).isEqualTo("other");
        assertThat(twice.priority()).isEqualTo(9);
        assertThat(twice.material()).isEqualTo("STONE");
    }

    // -- what the record refuses, and what it copies ------------------------------------------------------------

    @Test
    void theLoreIsCopiedSoALaterEditOfTheCallersListDoesNotReachTheItem() {
        List<String> lines = new ArrayList<>(List.of("first"));
        MenuItemSpec item = base().withLore(lines);

        lines.add("second");

        assertThat(item.lore())
                .as("a spec is a value, not a window onto the caller's list")
                .containsExactly("first");
        assertThatThrownBy(() -> item.lore().add("third")).isInstanceOf(UnsupportedOperationException.class);
    }

    // -- the historic constructors -----------------------------------------------------------------------------

    @Test
    void theTwelveArgumentFormDefaultsTheItemDragToEmpty() {
        MenuItemSpec item = new MenuItemSpec(
                SLOTS,
                0,
                "STONE",
                "",
                List.of(),
                DECOR,
                LoreMode.REPLACE,
                RequirementSpec.allOf(List.of()),
                CLICK,
                false,
                Optional.empty(),
                ItemType.NONE);

        assertThat(item.itemDrag()).isEmpty();
    }

    @Test
    void aFlatViewListLiftsIntoAnAllMandatoryBlock() {
        MenuItemSpec item = new MenuItemSpec(
                SLOTS,
                0,
                "STONE",
                "",
                List.of(),
                DECOR,
                LoreMode.REPLACE,
                List.of(Ref.parse("permission"), Ref.parse("money")),
                CLICK,
                false,
                Optional.empty(),
                ItemType.NONE);

        assertThat(item.view().minimum())
                .as("a flat list is every condition, not any of them")
                .isZero();
        assertThat(item.view().requirements())
                .extracting(requirement -> requirement.condition().id())
                .containsExactly("permission", "money");
    }

    @Test
    void theElevenArgumentFormKeepsTheHistoricReplaceLoreMode() {
        MenuItemSpec item = new MenuItemSpec(
                SLOTS, 0, "STONE", "", List.of(), DECOR, List.<Ref>of(), CLICK, false, Optional.empty(), ItemType.NONE);

        assertThat(item.loreMode())
                .as("a call site written before lore modes existed drew a replacing lore, and still does")
                .isEqualTo(LoreMode.REPLACE);
    }

    /** Asserts {@code copy} differs from a fresh {@link #base()} in {@code field} alone. */
    private static void assertOnlyChanged(MenuItemSpec copy, String field) {
        assertThat(copy)
                .usingRecursiveComparison()
                .ignoringFields(field)
                .as("a copy helper writes one field and carries the rest over untouched")
                .isEqualTo(base());
    }
}

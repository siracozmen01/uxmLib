package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class MenuSpecRecordsTest {

    @Test
    void clickSpecMergesAnyIntoSpecificKind() {
        var click = new ClickSpec(
                Map.of(
                        ClickKind.LEFT, List.of(Ref.parse("close")),
                        ClickKind.ANY, List.of(Ref.parse("sound:CLICK"))),
                Map.of());
        assertThat(click.actionsFor(ClickKind.LEFT)).extracting(Ref::id).containsExactly("close", "sound");
        assertThat(click.actionsFor(ClickKind.RIGHT)).extracting(Ref::id).containsExactly("sound");
    }

    @Test
    void menuSpecRejectsBadRows() {
        assertThatThrownBy(() ->
                        new MenuSpec("t", 7, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void menuSpecDefaultsClickCooldownToZero() {
        var spec = new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
        assertThat(spec.clickCooldownMs())
                .as("a menu built through the historic ctors sets no cooldown and defers to the global default")
                .isZero();
    }

    @Test
    void menuSpecDefaultsBedrockFormToEmpty() {
        var spec = new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
        assertThat(spec.bedrock())
                .as("a menu built through the historic ctors declares no bedrock {} block")
                .isEmpty();
    }

    @Test
    void menuItemSpecDefaultsItemDragToEmptyThroughTheHistoricConstructor() {
        var item = new MenuItemSpec(
                SlotSet.parse(List.of("0"), 9),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<Ref>of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
        assertThat(item.itemDrag())
                .as("an item built through a historic ctor carries no item-drag binding")
                .isEmpty();
    }

    @Test
    void menuSpecDefaultsBottomInventoryToFalseThroughTheHistoricConstructor() {
        var spec = new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
        assertThat(spec.bottomInventory())
                .as("a menu built through the historic ctors paints only the chest top")
                .isFalse();
    }

    @Test
    void menuSpecDefaultsChestOnlyToFalseThroughTheHistoricConstructor() {
        var spec = new MenuSpec("t", 1, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
        assertThat(spec.chestOnly())
                .as("a menu built through the historic ctors is a form candidate for a Bedrock viewer")
                .isFalse();
    }

    @Test
    void checkSlotsFitAcceptsABottomSlotOnlyWhenTheFlagIsSet() {
        Map<String, MenuItemSpec> items = Map.of("bot", itemAt(89));
        assertThatCode(() -> bottomMenu(items))
                .as("slot 89 fits the 90-slot canvas of a bottom-inventory menu")
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> plainMenu(items))
                .as("the same slot overflows a plain six-row chest with no bottom inventory")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePagedListIsTheOneDrawnNearestTheStartOfTheWindow() {
        Map<String, MenuItemSpec> items = new java.util.LinkedHashMap<>();
        items.put("kits", listAt(20));
        items.put("warps", listAt(3));
        items.put("filler", itemAt(1));

        assertThat(plainMenu(items).pagedListItem())
                .as("the warps open at slot 3 and the kits at slot 20, whatever order the map hands them over in")
                .contains(items.get("warps"));
    }

    @Test
    void aListIsMeasuredByTheSlotItOpensOnAndNotByTheSlotItEndsOn() {
        Map<String, MenuItemSpec> items = new java.util.LinkedHashMap<>();
        items.put("wide", listSpanning(0, 30));
        items.put("narrow", listSpanning(5, 6));

        assertThat(plainMenu(items).pagedListItem())
                .as("the wide list opens at slot 0, so it is the nearer one even though it ends far later")
                .contains(items.get("wide"));
    }

    @Test
    void twoListsDrawingIntoOneSlotAreRefusedWhereTheMenuIsBuilt() {
        Map<String, MenuItemSpec> items = new java.util.LinkedHashMap<>();
        items.put("warps", listNamed(3, "warp"));
        items.put("kits", listNamed(3, "kit"));

        assertThatThrownBy(() -> plainMenu(items))
                .as("two lists over one cell cannot both be seen, so the menu is named rather than half drawn")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kits")
                .hasMessageContaining("warps")
                .hasMessageContaining("slot 3");
    }

    @Test
    void aListMaySharplyAbutAnotherWithoutOverlappingIt() {
        Map<String, MenuItemSpec> items = new java.util.LinkedHashMap<>();
        items.put("warps", listSpanning(0, 1));
        items.put("kits", listSpanning(2, 3));

        assertThatCode(() -> plainMenu(items))
                .as("neighbouring lists are ordinary, only a shared cell is the mistake")
                .doesNotThrowAnyException();
    }

    @Test
    void aStaticItemMayStillSitOverAListCell() {
        Map<String, MenuItemSpec> items = new java.util.LinkedHashMap<>();
        items.put("warps", listSpanning(0, 1));
        items.put("frame", itemAt(1));

        assertThatCode(() -> plainMenu(items))
                .as("priority layering already decides a static item against a list cell, and operators use it")
                .doesNotThrowAnyException();
    }

    @Test
    void aMenuCarryingNoListNamesNoPagedItem() {
        assertThat(plainMenu(Map.of("filler", itemAt(1))).pagedListItem())
                .as("a menu with nothing to page has no page controls to point anywhere")
                .isEmpty();
    }

    /** A list-backed item drawing into two slots, the first of them {@code firstSlot}. */
    private static MenuItemSpec listAt(int firstSlot) {
        return listSpanning(firstSlot, firstSlot + 1);
    }

    /**
     * The same, carrying a name, so two lists claiming one slot are two different records. Without it they are equal
     * by value and an assertion cannot tell which of them the rule chose.
     */
    private static MenuItemSpec listNamed(int firstSlot, String name) {
        MenuItemSpec plain = listSpanning(firstSlot, firstSlot + 1);
        return new MenuItemSpec(
                plain.slots(),
                plain.priority(),
                plain.material(),
                name,
                plain.lore(),
                plain.decor(),
                plain.loreMode(),
                plain.view(),
                plain.click(),
                plain.update(),
                plain.list(),
                plain.type(),
                plain.itemDrag());
    }

    /** A list-backed item drawing into exactly two slots, {@code first} and {@code last}. */
    private static MenuItemSpec listSpanning(int first, int last) {
        MenuItemSpec template = itemAt(0);
        return new MenuItemSpec(
                SlotSet.parse(List.of(String.valueOf(first), String.valueOf(last)), 90),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<Ref>of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.of(new ListSpec(Ref.parse("src"), template)),
                ItemType.NONE);
    }

    /** A minimal static item occupying one slot, sized against the 90-slot bottom-inventory ceiling. */
    private static MenuItemSpec itemAt(int slot) {
        return new MenuItemSpec(
                SlotSet.parse(List.of(String.valueOf(slot)), 90),
                0,
                "STONE",
                "",
                List.of(),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.<Ref>of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    /** A six-row bottom-inventory menu carrying {@code items}: its slot-fit bound is 90. */
    private static MenuSpec bottomMenu(Map<String, MenuItemSpec> items) {
        return new MenuSpec(
                "t",
                6,
                new RefreshSpec(false, 0),
                List.of(),
                List.of(),
                List.of(),
                items,
                Optional.empty(),
                Map.of(),
                0L,
                true);
    }

    /** A six-row plain chest carrying {@code items}: its slot-fit bound is 54. */
    private static MenuSpec plainMenu(Map<String, MenuItemSpec> items) {
        return new MenuSpec("t", 6, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), items);
    }

    @Test
    void menuSpecRejectsNegativeClickCooldown() {
        assertThatThrownBy(() -> new MenuSpec(
                        "t",
                        1,
                        new RefreshSpec(false, 0),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        java.util.Optional.empty(),
                        Map.of(),
                        -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.menu.EntityListSpec;
import com.uxplima.uxmlib.menu.runtime.ListViewState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * One page of an entity list, painted. The renderer makes almost no presentation decision of its own: the icons are
 * the caller's, and what it owns is the geometry, which slot each entity lands in, which slots carry the buttons, and
 * that a page flip is a repaint rather than a re-query.
 *
 * <p>Every test paints into a window it can read back slot by slot, so the assertions are about where things went
 * rather than about what they looked like.
 */
class ListViewRendererTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    private final ListViewRenderer renderer = new ListViewRenderer();

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Three content slots at the top, nav at the bottom corners: the smallest list that still paginates. */
    private static EntityListSpec.Builder list(List<String> entities) {
        return EntityListSpec.builder()
                .title(Component.text("Warps"))
                .contentSlots(List.of(0, 1, 2))
                .navigation(45, 53, Material.ARROW)
                .navNames(Component.text("Back"), Component.text("Next"))
                .filler(FILLER)
                .entities(() -> List.copyOf(entities))
                .iconRenderer((who, entity) -> named(String.valueOf(entity)))
                .onSelect((who, entity) -> {});
    }

    /** An icon whose material carries the entity's identity, so a slot's contents name what landed there. */
    private static ItemStack named(String entity) {
        return new ItemStack(
                switch (entity) {
                    case "a" -> Material.STONE;
                    case "b" -> Material.DIRT;
                    case "c" -> Material.SAND;
                    case "d" -> Material.GRAVEL;
                    default -> Material.BEDROCK;
                });
    }

    private Inventory draw(EntityListSpec spec, ListViewState state, int page) {
        Inventory inv = Bukkit.createInventory(null, spec.rows() * 9);
        renderer.populate(inv, spec, state, viewer, page);
        return inv;
    }

    private static Material at(Inventory inv, int slot) {
        ItemStack stack = inv.getItem(slot);
        return stack == null ? Material.AIR : stack.getType();
    }

    // -- the geometry of one page -----------------------------------------------------------------------------

    @Test
    void eachEntityLandsInTheContentSlotAtItsOwnPosition() {
        Inventory inv = draw(list(List.of("a", "b", "c")).build(), new ListViewState("spec"), 0);

        assertThat(at(inv, 0)).isEqualTo(Material.STONE);
        assertThat(at(inv, 1)).isEqualTo(Material.DIRT);
        assertThat(at(inv, 2)).isEqualTo(Material.SAND);
    }

    /**
     * A page holds as many entities as the spec declared content slots, so the fourth goes to the next page rather
     * than to a slot the spec never offered.
     */
    @Test
    void thePageHoldsAsManyEntitiesAsThereAreContentSlots() {
        EntityListSpec spec = list(List.of("a", "b", "c", "d")).build();

        assertThat(at(draw(spec, new ListViewState("spec"), 0), 0)).isEqualTo(Material.STONE);
        assertThat(at(draw(spec, new ListViewState("spec"), 1), 0)).isEqualTo(Material.GRAVEL);
    }

    /** A content slot with no entity to hold shows the filler, not the entity the previous page drew there. */
    @Test
    void aContentSlotWithNoEntityIsLeftAsFiller() {
        Inventory inv = draw(list(List.of("a", "b", "c", "d")).build(), new ListViewState("spec"), 1);

        assertThat(at(inv, 0)).isEqualTo(Material.GRAVEL);
        assertThat(at(inv, 1)).isEqualTo(FILLER);
        assertThat(at(inv, 2)).isEqualTo(FILLER);
    }

    @Test
    void everySlotTheSpecDoesNotClaimCarriesTheFiller() {
        Inventory inv = draw(list(List.of("a")).build(), new ListViewState("spec"), 0);

        assertThat(at(inv, 3)).isEqualTo(FILLER);
        assertThat(at(inv, 22)).isEqualTo(FILLER);
        assertThat(at(inv, 44)).isEqualTo(FILLER);
    }

    @Test
    void bothNavButtonsArePaintedAtTheSlotsTheSpecNamed() {
        Inventory inv = draw(list(List.of("a")).build(), new ListViewState("spec"), 0);

        assertThat(at(inv, 45)).isEqualTo(Material.ARROW);
        assertThat(at(inv, 53)).isEqualTo(Material.ARROW);
    }

    // -- what a click can reach -------------------------------------------------------------------------------

    @Test
    void theRoutingRemembersWhichEntityWasDrawnInWhichSlot() {
        ListViewState state = new ListViewState("spec");
        draw(list(List.of("a", "b")).build(), state, 0);

        assertThat(state.entityAt(0)).contains("a");
        assertThat(state.entityAt(1)).contains("b");
        assertThat(state.entityAt(2)).as("no entity was drawn there").isEmpty();
    }

    /**
     * A page flip repaints, and the routing must be the new page's. The renderer does not clear the state itself (its
     * caller does), so this draws each page onto its own state, which is the contract the caller keeps.
     */
    @Test
    void theSecondPageRoutesItsOwnEntitiesRatherThanTheFirstPages() {
        EntityListSpec spec = list(List.of("a", "b", "c", "d")).build();
        ListViewState second = new ListViewState("spec");
        draw(spec, second, 1);

        assertThat(second.entityAt(0)).contains("d");
    }

    @Test
    void bothNavSlotsAreRoutedAsNavigation() {
        ListViewState state = new ListViewState("spec");
        draw(list(List.of("a")).build(), state, 0);

        assertThat(state.isPrev(45)).isTrue();
        assertThat(state.isNext(53)).isTrue();
        assertThat(state.isPrev(53)).isFalse();
    }

    // -- the page the caller asked for versus the one it got ---------------------------------------------------

    /**
     * The clamped page is handed back so the holder can stamp it, rather than the page that was asked for. A viewer
     * mashing the next arrow past the end must not leave the holder believing it is on a page that was never drawn.
     */
    @Test
    void aPagePastTheEndIsClampedAndTheDrawnPageIsReported() {
        EntityListSpec spec = list(List.of("a", "b", "c", "d")).build();
        Inventory inv = Bukkit.createInventory(null, spec.rows() * 9);

        int drawn = renderer.populate(inv, spec, new ListViewState("spec"), viewer, 9);

        assertThat(drawn).isEqualTo(1);
        assertThat(at(inv, 0)).isEqualTo(Material.GRAVEL);
    }

    @Test
    void aNegativePageIsClampedToTheFirst() {
        EntityListSpec spec = list(List.of("a", "b", "c", "d")).build();
        Inventory inv = Bukkit.createInventory(null, spec.rows() * 9);

        int drawn = renderer.populate(inv, spec, new ListViewState("spec"), viewer, -3);

        assertThat(drawn).isZero();
        assertThat(at(inv, 0)).isEqualTo(Material.STONE);
    }

    @Test
    void anEmptyListStillDrawsAWindowRatherThanFailing() {
        Inventory inv = draw(list(List.of()).build(), new ListViewState("spec"), 0);

        assertThat(at(inv, 0)).isEqualTo(FILLER);
        assertThat(at(inv, 45)).isEqualTo(Material.ARROW);
    }

    // -- the optional buttons ---------------------------------------------------------------------------------

    /**
     * A list that declares no create button leaves that slot as ordinary filler. The renderer also guards each of the
     * button's three settings separately, but the builder sets all three in one call, so a half-declared button is a
     * state no caller can reach and there is nothing here to drive it with.
     */
    @Test
    void aListWithNoCreateButtonLeavesThatSlotAsFiller() {
        Inventory inv = draw(list(List.of("a")).build(), new ListViewState("spec"), 0);

        assertThat(at(inv, 40)).isEqualTo(FILLER);
    }

    @Test
    void aFullyDeclaredCreateButtonIsPaintedAndRoutedToItsHandler() {
        List<String> pressed = new ArrayList<>();
        ListViewState state = new ListViewState("spec");
        Inventory inv = draw(
                list(List.of("a"))
                        .onCreate(40, Material.EMERALD, Component.text("New"), who -> pressed.add("create"))
                        .build(),
                state,
                0);

        assertThat(at(inv, 40)).isEqualTo(Material.EMERALD);
        state.buttonAt(40).orElseThrow().run();
        assertThat(pressed).containsExactly("create");
    }

    @Test
    void everyExtraButtonIsPaintedAsGivenAndRoutedToItsOwnHandler() {
        List<String> pressed = new ArrayList<>();
        ListViewState state = new ListViewState("spec");
        Inventory inv = draw(
                list(List.of("a"))
                        .extraButtons(List.of(
                                new EntityListSpec.ExtraButton(
                                        41, new ItemStack(Material.BOOK), who -> pressed.add("one")),
                                new EntityListSpec.ExtraButton(
                                        42, new ItemStack(Material.PAPER), who -> pressed.add("two"))))
                        .build(),
                state,
                0);

        assertThat(at(inv, 41)).isEqualTo(Material.BOOK);
        assertThat(at(inv, 42)).isEqualTo(Material.PAPER);
        state.buttonAt(42).orElseThrow().run();
        assertThat(pressed).containsExactly("two");
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert each requireNonNull guard fires
    void theRendererRefusesANullWindowSpecStateOrViewer() {
        EntityListSpec spec = list(List.of("a")).build();
        Inventory inv = Bukkit.createInventory(null, 54);
        ListViewState state = new ListViewState("spec");

        assertThatNullPointerException().isThrownBy(() -> renderer.populate(null, spec, state, viewer, 0));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, null, state, viewer, 0));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, spec, null, viewer, 0));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, spec, state, null, 0));
    }
}

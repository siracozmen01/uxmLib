package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The recipe a paginated entity list is drawn from. It is type-erased on the entity and every button name is a
 * component the caller already resolved, so almost nothing here is a look decision. What is worth pinning is the
 * validation: which fields a caller may leave out, which it may not, and what the spec does with the collections it
 * is handed.
 */
class EntityListSpecTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A builder with every required field set, so a test can leave out exactly one and see which guard fires. */
    private static EntityListSpec.Builder full() {
        return EntityListSpec.builder()
                .title(Component.text("Warps"))
                .contentSlots(List.of(0, 1, 2))
                .navigation(45, 53, Material.ARROW)
                .navNames(Component.text("Back"), Component.text("Next"))
                .filler(Material.GRAY_STAINED_GLASS_PANE)
                .entities(List::of)
                .iconRenderer((viewer, entity) -> new ItemStack(Material.STONE))
                .onSelect((viewer, entity) -> {});
    }

    // -- the geometry -----------------------------------------------------------------------------------------

    @Test
    void aListIsSixRowsUnlessTheCallerSaysOtherwise() {
        assertThat(full().build().rows()).isEqualTo(6);
    }

    @Test
    void theRowCountsAWindowCanHaveAreAccepted() {
        assertThat(full().rows(1).build().rows()).isEqualTo(1);
        assertThat(full().rows(6).build().rows()).isEqualTo(6);
    }

    /** A row count no inventory has is a caller mistake, not an operator's, so it fails at build rather than at open. */
    @Test
    void aRowCountNoWindowHasIsRefusedAtBuildAndSaysWhatItWas() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> full().rows(0).build())
                .withMessageContaining("was 0");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> full().rows(7).build())
                .withMessageContaining("was 7");
    }

    // -- what the caller may leave out ------------------------------------------------------------------------

    @Test
    void aSpecWithNoTitleIsRefusedByName() {
        EntityListSpec.Builder untitled = EntityListSpec.builder()
                .contentSlots(List.of(0))
                .navigation(45, 53, Material.ARROW)
                .navNames(Component.text("Back"), Component.text("Next"))
                .filler(Material.GRAY_STAINED_GLASS_PANE)
                .entities(List::of)
                .iconRenderer((viewer, entity) -> new ItemStack(Material.STONE))
                .onSelect((viewer, entity) -> {});

        assertThatNullPointerException().isThrownBy(untitled::build).withMessageContaining("title");
    }

    @Test
    void aSpecWithNoEntitySourceIsRefusedByName() {
        EntityListSpec.Builder sourceless = EntityListSpec.builder()
                .title(Component.text("Warps"))
                .contentSlots(List.of(0))
                .navigation(45, 53, Material.ARROW)
                .navNames(Component.text("Back"), Component.text("Next"))
                .filler(Material.GRAY_STAINED_GLASS_PANE)
                .iconRenderer((viewer, entity) -> new ItemStack(Material.STONE))
                .onSelect((viewer, entity) -> {});

        assertThatNullPointerException().isThrownBy(sourceless::build).withMessageContaining("entities");
    }

    @Test
    void aSpecWithNoIconRendererIsRefusedByName() {
        EntityListSpec.Builder unpainted = EntityListSpec.builder()
                .title(Component.text("Warps"))
                .contentSlots(List.of(0))
                .navigation(45, 53, Material.ARROW)
                .navNames(Component.text("Back"), Component.text("Next"))
                .filler(Material.GRAY_STAINED_GLASS_PANE)
                .entities(List::of)
                .onSelect((viewer, entity) -> {});

        assertThatNullPointerException().isThrownBy(unpainted::build).withMessageContaining("iconRenderer");
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the setter guards fire at the call, not later
    void aSetterHandedNullFailsWhereItWasCalledRatherThanAtBuild() {
        assertThatNullPointerException()
                .isThrownBy(() -> EntityListSpec.builder().title(null))
                .withMessageContaining("title");
        assertThatNullPointerException()
                .isThrownBy(() -> EntityListSpec.builder().filler(null))
                .withMessageContaining("filler");
    }

    // -- the collections it is handed -------------------------------------------------------------------------

    @Test
    void theContentSlotsAreCopiedSoTheCallerCannotMoveThemAfterwards() {
        List<Integer> slots = new ArrayList<>(List.of(0, 1, 2));
        EntityListSpec spec = full().contentSlots(slots).build();

        slots.clear();

        assertThat(spec.contentSlots()).containsExactly(0, 1, 2);
    }

    @Test
    void theContentSlotsHandedOutCannotBeWrittenTo() {
        EntityListSpec spec = full().build();

        assertThatThrownBy(() -> spec.contentSlots().add(9)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theExtraButtonsAreCopiedTheSameWay() {
        List<EntityListSpec.ExtraButton> buttons = new ArrayList<>();
        buttons.add(new EntityListSpec.ExtraButton(8, new ItemStack(Material.PAPER), viewer -> {}));
        EntityListSpec spec = full().extraButtons(buttons).build();

        buttons.clear();

        assertThat(spec.extraButtons()).hasSize(1);
    }

    @Test
    void aListWithNoExtraButtonsHasNoneRatherThanNull() {
        assertThat(full().build().extraButtons()).isEmpty();
    }

    // -- the optional buttons ---------------------------------------------------------------------------------

    @Test
    void aListWithNoCreateOrActionButtonOffersNeitherSlot() {
        EntityListSpec spec = full().build();

        assertThat(spec.createSlot()).isEmpty();
        assertThat(spec.actionSlot()).isEmpty();
        assertThat(spec.onCreate()).isEmpty();
        assertThat(spec.createName()).isEmpty();
    }

    @Test
    void aWiredCreateButtonOffersItsSlotIconNameAndHandler() {
        EntityListSpec spec = full().onCreate(4, Material.EMERALD, Component.text("New"), viewer -> {})
                .build();

        assertThat(spec.createSlot()).hasValue(4);
        assertThat(spec.createIcon()).isEqualTo(Material.EMERALD);
        assertThat(spec.createName()).contains(Component.text("New"));
        assertThat(spec.onCreate()).isPresent();
    }

    @Test
    void aWiredActionButtonOffersItsSlotIconNameAndHandler() {
        EntityListSpec spec = full().onAction(5, Material.COMPASS, Component.text("Sort"), viewer -> {})
                .build();

        assertThat(spec.actionSlot()).hasValue(5);
        assertThat(spec.actionIcon()).isEqualTo(Material.COMPASS);
        assertThat(spec.actionName()).contains(Component.text("Sort"));
        assertThat(spec.onAction()).isPresent();
    }

    // -- the entity source ------------------------------------------------------------------------------------

    /**
     * The accessor reads like a field and is a call. It is asked once per draw today, by the one renderer that owns
     * this spec, and this states that it is not memoised so nobody adds a second ask believing the first was cached.
     */
    @Test
    void theEntitySourceIsAskedAgainEveryTimeTheAccessorIsCalled() {
        int[] asks = {0};
        EntityListSpec spec = full().entities(() -> {
                    asks[0]++;
                    return List.of("a", "b");
                })
                .build();

        assertThat(spec.entities()).containsExactly("a", "b");
        assertThat(spec.entities()).containsExactly("a", "b");
        assertThat(asks[0]).isEqualTo(2);
    }

    // -- an extra button --------------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the record's compact constructor guards fire
    void anExtraButtonWithNoIconOrNoHandlerIsRefusedByName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EntityListSpec.ExtraButton(1, null, viewer -> {}))
                .withMessageContaining("icon");
        assertThatNullPointerException()
                .isThrownBy(() -> new EntityListSpec.ExtraButton(1, new ItemStack(Material.PAPER), null))
                .withMessageContaining("onClick");
    }
}

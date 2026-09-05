package com.uxplima.uxmlib.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.EditorSpec;
import com.uxplima.uxmlib.menu.EntityEditorLayout;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.runtime.EditorState;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * One editor window, painted. The renderer owns the geometry and one presentation decision the canon makes for every
 * plugin: a property's own label is the title line of its lore, and the button's display name is blank, so the
 * tooltip opens on the setting the viewer is looking at rather than on the generic word every property shares.
 *
 * <p>The property list is a function of the subject and is re-read on every draw, so a value changed by a click shows
 * on the next one. That is asserted by drawing twice against a list that has changed in between, which is the only
 * way to tell a re-read from a list captured when the spec was built.
 */
class EditorRendererTest {

    /** A catalogue that hands every key straight back, so a rendered line is readable in an assertion. */
    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            String out = key;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            return Component.text(out);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** A property that reports a fixed label, value and icon and records nothing else. */
    private record Fixed(String label, String value, Material icon) implements EditableProperty {

        @Override
        public String valueLore(Player viewer) {
            return value;
        }

        @Override
        public void onClick(PropertyClick click) {}
    }

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final List<EditableProperty> properties = new ArrayList<>();

    private final List<String> pressed = new ArrayList<>();

    private EditorRenderer renderer;

    private Player viewer;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        renderer = new EditorRenderer(new PlainText(), Theme::defaults);
        properties.clear();
        properties.add(new Fixed("difficulty", "hard", Material.DIAMOND));
        properties.add(new Fixed("public", "on", Material.EMERALD));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EditorSpec.Builder editor(EntityEditorLayout layout) {
        return EditorSpec.builder()
                .layout(layout)
                .title((who, subject) -> Component.text("Editing"))
                .valueLore("value: %value%")
                .backName("back")
                .properties(subject -> List.copyOf(properties))
                .onBack(who -> pressed.add("back"));
    }

    private static EntityEditorLayout layout() {
        return EntityEditorLayout.codeDefault(List.of(10, 11, 12), 26);
    }

    private Inventory draw(EditorSpec spec, EditorState state) {
        Inventory inv = Bukkit.createInventory(null, spec.layout().rows() * 9);
        renderer.populate(inv, spec, state, viewer);
        return inv;
    }

    private static Material at(Inventory inv, int slot) {
        ItemStack stack = inv.getItem(slot);
        return stack == null ? Material.AIR : stack.getType();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> loreOf(Inventory inv, int slot) {
        List<Component> lore = inv.getItem(slot).lore();
        List<String> out = new ArrayList<>();
        for (Component line : lore == null ? List.<Component>of() : lore) {
            out.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return out;
    }

    // -- the geometry -----------------------------------------------------------------------------------------

    @Test
    void eachPropertyLandsOnTheLayoutSlotAtItsOwnPosition() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(at(inv, 10)).isEqualTo(Material.DIAMOND);
        assertThat(at(inv, 11)).isEqualTo(Material.EMERALD);
    }

    @Test
    void aLayoutSlotWithNoPropertyToHoldIsLeftAsFiller() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(at(inv, 12)).isEqualTo(FILLER);
    }

    @Test
    void everySlotTheLayoutDoesNotClaimCarriesTheFiller() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(at(inv, 0)).isEqualTo(FILLER);
        assertThat(at(inv, 13)).isEqualTo(FILLER);
    }

    /**
     * A subject with more properties than the layout has slots draws as many as fit and drops the rest. Pinning the
     * behaviour rather than endorsing it: an operator who adds a property to a full layout sees nothing happen, and
     * the renderer says nothing about it.
     */
    @Test
    void propertiesPastTheLayoutsLastSlotAreDroppedWithoutASign() {
        properties.add(new Fixed("third", "3", Material.GOLD_INGOT));
        properties.add(new Fixed("fourth", "4", Material.IRON_INGOT));
        EditorState state = new EditorState("spec", "subject");

        Inventory inv = draw(editor(layout()).build(), state);

        assertThat(at(inv, 12)).isEqualTo(Material.GOLD_INGOT);
        assertThat(state.propertyAt(12)).isPresent();
        assertThat(inv.all(Material.IRON_INGOT)).isEmpty();
    }

    // -- the one presentation decision the renderer makes --------------------------------------------------------

    /**
     * The canon keeps a tile's title on the first lore line and leaves the display name blank. Putting the label in
     * the name would move it outside the tooltip body and leave the lore opening on the generic word every property
     * shares.
     *
     * <p>The assertion is about what the name does not carry rather than its exact text, because this runtime
     * decorates a display name with brackets of its own ({@code Tiles.blankName()} comes back as {@code [ ]}). Those
     * brackets are the mock's and not the renderer's, so pinning them would pin the test runtime.
     */
    @Test
    void thePropertyLabelIsTheTitleOfItsLoreRatherThanItsDisplayName() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        String name = plain(inv.getItem(10).displayName());

        assertThat(name).as("the label does not go in the display name").doesNotContain("difficulty");
        assertThat(name.replaceAll("[\\[\\]\\s]", ""))
                .as("and the name carries no word of its own")
                .isEmpty();
        assertThat(loreOf(inv, 10)).first().asString().contains("difficulty");
    }

    @Test
    void thePropertysCurrentValueGoesThroughTheSpecsValueLoreLine() {
        Inventory inv = draw(editor(layout()).build(), new EditorState("spec", "subject"));

        assertThat(loreOf(inv, 10)).anyMatch(line -> line.contains("value: hard"));
    }

    // -- what a click can reach ---------------------------------------------------------------------------------

    @Test
    void theRoutingRemembersWhichPropertyWasDrawnInWhichSlot() {
        EditorState state = new EditorState("spec", "subject");
        draw(editor(layout()).build(), state);

        assertThat(state.propertyAt(10))
                .get()
                .extracting(EditableProperty::label)
                .isEqualTo("difficulty");
        assertThat(state.propertyAt(11))
                .get()
                .extracting(EditableProperty::label)
                .isEqualTo("public");
        assertThat(state.propertyAt(12)).isEmpty();
    }

    @Test
    void theBackButtonIsPaintedAtItsSlotAndRoutedToTheSpecsHandler() {
        EditorState state = new EditorState("spec", "subject");
        Inventory inv = draw(editor(layout()).build(), state);

        assertThat(at(inv, 26)).isEqualTo(Material.ARROW);
        state.buttonAt(26).orElseThrow().run();
        assertThat(pressed).containsExactly("back");
    }

    @Test
    void anEditorWithNoDeleteHandlerPaintsNoDeleteButton() {
        EditorState state = new EditorState("spec", "subject");
        Inventory inv = draw(
                editor(EntityEditorLayout.withDelete(List.of(10, 11), 26, 25)).build(), state);

        assertThat(at(inv, 25)).isEqualTo(FILLER);
        assertThat(state.buttonAt(25)).isEmpty();
    }

    @Test
    void aDeclaredDeleteButtonIsPaintedAndRoutedWithTheSubjectItWouldDelete() {
        List<Object> deleted = new ArrayList<>();
        EditorState state = new EditorState("spec", "a-warp");
        Inventory inv = draw(
                editor(EntityEditorLayout.withDelete(List.of(10, 11), 26, 25))
                        .onDelete("delete", "really?", (who, subject) -> deleted.add(subject))
                        .build(),
                state);

        assertThat(at(inv, 25)).isEqualTo(Material.BARRIER);
        state.buttonAt(25).orElseThrow().run();
        assertThat(deleted).containsExactly("a-warp");
    }

    // -- the list is a function of the subject, not a snapshot ---------------------------------------------------

    /**
     * A click changes the subject and the window is repainted, so the property list must be asked again rather than
     * captured when the spec was built. Drawing twice across a change is the only way to tell those apart.
     */
    @Test
    void thePropertyListIsReReadOnEveryDrawRatherThanCapturedOnce() {
        EditorSpec spec = editor(layout()).build();
        draw(spec, new EditorState("spec", "subject"));

        properties.set(0, new Fixed("difficulty", "easy", Material.DIAMOND));
        Inventory second = draw(spec, new EditorState("spec", "subject"));

        assertThat(loreOf(second, 10)).anyMatch(line -> line.contains("value: easy"));
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert each requireNonNull guard fires
    void theRendererRefusesANullWindowSpecStateOrViewer() {
        EditorSpec spec = editor(layout()).build();
        Inventory inv = Bukkit.createInventory(null, 27);
        EditorState state = new EditorState("spec", "subject");

        assertThatNullPointerException().isThrownBy(() -> renderer.populate(null, spec, state, viewer));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, null, state, viewer));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, spec, null, viewer));
        assertThatNullPointerException().isThrownBy(() -> renderer.populate(inv, spec, state, null));
    }
}

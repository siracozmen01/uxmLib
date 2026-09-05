package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.ConditionRegistry;
import com.uxplima.uxmlib.menu.binding.ListSourceRegistry;
import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import com.uxplima.uxmlib.menu.property.PropertyClick;
import com.uxplima.uxmlib.menu.render.EditorRenderer;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The typed face a module puts on the engine's editor. It owns almost no behaviour of its own: it folds what a caller
 * hands the builder into an {@link EditorSpec} and opens it. So what is worth pinning is the folding, and the one
 * mapping the view exposes on its own, {@code propertyAt}.
 *
 * <p>The property list is a function of the entity rather than a list, which is the point of the type parameter: two
 * entities opened through one view see their own properties, and an entity whose properties change between two reads
 * shows the new ones. A test that only ever opened one entity could not tell that from a list captured at build time.
 */
class EntityEditorViewTest {

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

    /** A property that reports a fixed label and value and records nothing else. */
    private record Fixed(String label, String value) implements EditableProperty {

        @Override
        public Material icon() {
            return Material.DIAMOND;
        }

        @Override
        public String valueLore(Player viewer) {
            return value;
        }

        @Override
        public void onClick(PropertyClick click) {}
    }

    /** The entity this view edits: a name and its own property list, so two of them cannot share one. */
    private record Subject(String name, List<EditableProperty> properties) {}

    private final List<String> backs = new ArrayList<>();

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

    private static Menus engine(boolean withEditorSupport) {
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, new PlaceholderRegistry()), new ConditionRegistry());
        EditorRenderer editor = withEditorSupport ? new EditorRenderer(new PlainText(), Theme::defaults) : null;
        return new Menus(renderer, new SameThreadScheduler(), new ListSourceRegistry(), editor);
    }

    private EntityEditorView.Builder<Subject> view(Menus menus, EntityEditorLayout layout) {
        return EntityEditorView.<Subject>builder()
                .menus(menus)
                .guiText(new PlainText())
                .layout(layout)
                .title((who, subject) -> Component.text("editing " + subject.name()))
                .valueLore("value: %value%")
                .backName("back")
                .properties(Subject::properties)
                .onBack(who -> backs.add(who.getName()));
    }

    private static EntityEditorLayout threeSlots() {
        return EntityEditorLayout.codeDefault(List.of(10, 12, 14), 22);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> loreOf(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null || item.getItemMeta().lore() == null) {
            return List.of();
        }
        return item.getItemMeta().lore().stream()
                .map(EntityEditorViewTest::plain)
                .toList();
    }

    // -- what the builder insists on --------------------------------------------------------------------------

    /**
     * The builder validates at {@code build}, not at each setter, so a caller that forgot one field learns which one
     * from the failure rather than from a null pointer thrown later inside an open.
     */
    @Test
    void buildingWithoutTheEngineNamesTheEngine() {
        assertThatNullPointerException()
                .isThrownBy(() -> EntityEditorView.<Subject>builder().build())
                .withMessageContaining("menus");
    }

    @Test
    void buildingWithoutAPropertyProviderNamesIt() {
        assertThatNullPointerException()
                .isThrownBy(() -> EntityEditorView.<Subject>builder()
                        .menus(engine(true))
                        .guiText(new PlainText())
                        .layout(threeSlots())
                        .title((who, subject) -> Component.empty())
                        .valueLore("value: %value%")
                        .backName("back")
                        .build())
                .withMessageContaining("properties");
    }

    // -- the slot to property mapping -------------------------------------------------------------------------

    /** The i-th property goes in the i-th of the layout's property slots, in the order the layout wrote them. */
    @Test
    void theNthPropertyAnswersForTheNthSlotTheLayoutNamed() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();
        Subject subject = new Subject("home", List.of(new Fixed("difficulty", "hard"), new Fixed("public", "on")));

        assertThat(view.propertyAt(10, subject)).map(EditableProperty::label).contains("difficulty");
        assertThat(view.propertyAt(12, subject)).map(EditableProperty::label).contains("public");
    }

    /** A layout slot the property list does not reach carries nothing, so a click there edits nothing. */
    @Test
    void aPropertySlotPastTheEndOfTheListAnswersNothing() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();
        Subject subject = new Subject("home", List.of(new Fixed("difficulty", "hard")));

        assertThat(view.propertyAt(12, subject)).isEmpty();
        assertThat(view.propertyAt(14, subject)).isEmpty();
    }

    @Test
    void aSlotTheLayoutNeverNamedAsAPropertySlotAnswersNothing() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();
        Subject subject = new Subject("home", List.of(new Fixed("difficulty", "hard")));

        assertThat(view.propertyAt(22, subject))
                .as("the back slot is not a property slot")
                .isEmpty();
        assertThat(view.propertyAt(0, subject)).isEmpty();
    }

    /**
     * One view, two entities, two property lists. The list is a function of the entity, and only asking twice with
     * different entities tells that apart from a list the builder captured once.
     */
    @Test
    void twoEntitiesOpenedThroughOneViewEachSeeTheirOwnProperties() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();
        Subject home = new Subject("home", List.of(new Fixed("difficulty", "hard")));
        Subject shop = new Subject("shop", List.of(new Fixed("currency", "gold")));

        assertThat(view.propertyAt(10, home)).map(EditableProperty::label).contains("difficulty");
        assertThat(view.propertyAt(10, shop)).map(EditableProperty::label).contains("currency");
    }

    // -- the window the view opens ----------------------------------------------------------------------------

    @Test
    void theOpenedWindowIsTitledForTheEntityItWasOpenedWith() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();

        view.open(viewer, new Subject("home", List.of(new Fixed("difficulty", "hard"))));

        assertThat(plain(viewer.getOpenInventory().title())).contains("editing home");
    }

    /** Each property is drawn at its slot, and the value the caller's lore line asked for is filled in. */
    @Test
    void everyPropertyIsDrawnAtItsSlotWithTheCallersValueLine() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();

        view.open(viewer, new Subject("home", List.of(new Fixed("difficulty", "hard"), new Fixed("public", "on"))));

        Inventory inv = viewer.getOpenInventory().getTopInventory();
        assertThat(loreOf(inv, 10)).anyMatch(line -> line.contains("value: hard"));
        assertThat(loreOf(inv, 12)).anyMatch(line -> line.contains("value: on"));
    }

    @Test
    void theWindowIsAsManyRowsAsTheLayoutAsksFor() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();

        view.open(viewer, new Subject("home", List.of(new Fixed("difficulty", "hard"))));

        assertThat(viewer.getOpenInventory().getTopInventory().getSize()).isEqualTo(27);
    }

    /** A view built without a delete handler draws no delete button, whatever the layout offers a slot for. */
    @Test
    void aViewWithNoDeleteHandlerDrawsNoDeleteButtonEvenWhereTheLayoutHasASlotForOne() {
        EntityEditorLayout layout = EntityEditorLayout.withDelete(List.of(10, 12), 22, 26);
        EntityEditorView<Subject> view = view(engine(true), layout).build();

        view.open(viewer, new Subject("home", List.of(new Fixed("difficulty", "hard"))));

        ItemStack drawn = viewer.getOpenInventory().getTopInventory().getItem(26);
        assertThat(drawn == null ? Material.AIR : drawn.getType())
                .as("the delete slot holds the filler, not a barrier")
                .isNotEqualTo(Material.BARRIER);
    }

    @Test
    void aViewWiredWithADeleteHandlerDrawsTheDeleteButtonAtTheLayoutsSlot() {
        EntityEditorLayout layout = EntityEditorLayout.withDelete(List.of(10, 12), 22, 26);
        List<String> deleted = new ArrayList<>();
        EntityEditorView<Subject> view = view(engine(true), layout)
                .onDelete("delete", "really delete?", (who, subject) -> deleted.add(subject.name()))
                .build();

        view.open(viewer, new Subject("home", List.of(new Fixed("difficulty", "hard"))));

        ItemStack drawn = viewer.getOpenInventory().getTopInventory().getItem(26);
        assertThat(drawn).isNotNull();
        assertThat(drawn.getType()).isEqualTo(Material.BARRIER);
        assertThat(deleted).as("drawing the button deletes nothing").isEmpty();
    }

    /**
     * An engine wired without an editor renderer cannot open an editor. It is a wiring mistake rather than a runtime
     * condition, so it fails where it is made rather than opening an empty window.
     */
    @Test
    void openingThroughAnEngineWithNoEditorSupportFailsLoudly() {
        EntityEditorView<Subject> view = view(engine(false), threeSlots()).build();

        assertThatIllegalStateException()
                .isThrownBy(() -> view.open(viewer, new Subject("home", List.of(new Fixed("difficulty", "hard")))))
                .withMessageContaining("editor support");
    }

    @Test
    void openRefusesANullViewerOrEntityRatherThanOpeningAnEmptyWindow() {
        EntityEditorView<Subject> view = view(engine(true), threeSlots()).build();

        assertThatNullPointerException()
                .isThrownBy(() -> view.open(viewer, null))
                .withMessageContaining("entity");
    }
}

package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.OptionalInt;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.menu.property.EditableProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The recipe a typed property editor is drawn from. Two things here are worth a test rather than a reading: the
 * delete button is optional and is only half a button unless all four of its parts are present, and the property
 * list is a function of the subject rather than a snapshot, which is what makes an edit show without a reopen.
 */
class EditorSpecTest {

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

    @Test
    void anEditorWiredWithoutADeleteHandlerShowsNoDeleteButton() {
        EditorSpec spec =
                base(EntityEditorLayout.withDelete(List.of(11), 22, 26)).build();

        assertThat(spec.hasDelete())
                .as("a slot in the layout is not a button without a handler")
                .isFalse();
        assertThat(spec.onDelete()).isEmpty();
        assertThat(spec.deleteName()).isEmpty();
        assertThat(spec.deleteConfirmTitle()).isEmpty();
    }

    @Test
    void anEditorWiredWithADeleteHandlerButNoSlotShowsNoDeleteButton() {
        EditorSpec spec = base(EntityEditorLayout.codeDefault(List.of(11), 22))
                .onDelete("gui.delete", "gui.delete.confirm", (player, subject) -> {})
                .build();

        assertThat(spec.hasDelete())
                .as("a handler with nowhere to draw its button would delete on a slot the operator never wrote")
                .isFalse();
        assertThat(spec.onDelete())
                .as("the handler is still carried, it just has no button")
                .isNotEmpty();
    }

    @Test
    void anEditorWithAllFourPartsShowsTheDeleteButton() {
        EditorSpec spec = base(EntityEditorLayout.withDelete(List.of(11), 22, 26))
                .onDelete("gui.delete", "gui.delete.confirm", (player, subject) -> {})
                .build();

        assertThat(spec.hasDelete()).isTrue();
        assertThat(spec.deleteName()).contains("gui.delete");
        assertThat(spec.deleteConfirmTitle()).contains("gui.delete.confirm");
    }

    @Test
    void theDeleteHandlerIsHandedTheViewerAndTheSubjectItWouldDelete() {
        Object[] seen = new Object[2];
        EditorSpec spec = base(EntityEditorLayout.withDelete(List.of(11), 22, 26))
                .onDelete("gui.delete", "gui.delete.confirm", (player, subject) -> {
                    seen[0] = player;
                    seen[1] = subject;
                })
                .build();

        spec.onDelete().orElseThrow().accept(viewer, "spawn");

        assertThat(seen[0]).isSameAs(viewer);
        assertThat(seen[1]).isEqualTo("spawn");
    }

    @Test
    void thePropertiesAreReadFromTheSubjectOnEveryDrawRatherThanSnapshotted() {
        List<EditableProperty> first = List.of();
        List<EditableProperty> second = List.of();
        EditorSpec spec = base(EntityEditorLayout.codeDefault(List.of(11), 22))
                .properties(subject -> "spawn".equals(subject) ? first : second)
                .build();

        assertThat(spec.propertiesFor("spawn")).isSameAs(first);
        assertThat(spec.propertiesFor("shop"))
                .as("a different subject is a different list, on the same spec")
                .isSameAs(second);
    }

    @Test
    void anEditorOpenedOnNothingStillAsksItsPropertyProvider() {
        boolean[] asked = {false};
        EditorSpec spec = base(EntityEditorLayout.codeDefault(List.of(11), 22))
                .properties(subject -> {
                    asked[0] = true;
                    return List.of();
                })
                .build();

        assertThat(spec.propertiesFor(null)).isEmpty();
        assertThat(asked[0])
                .as("a null subject is an editor for something not created yet, not a failure")
                .isTrue();
    }

    @Test
    void theTitleIsResolvedPerViewerAndSubject() {
        EditorSpec spec = base(EntityEditorLayout.codeDefault(List.of(11), 22))
                .title((player, subject) -> Component.text(player.getName() + ":" + subject))
                .build();

        assertThat(plain(spec.title(viewer, "spawn"))).isEqualTo(viewer.getName() + ":spawn");
    }

    @Test
    void aTitleAskedForWithoutAViewerIsRefusedRatherThanDrawnBlank() {
        EditorSpec spec = base(EntityEditorLayout.codeDefault(List.of(11), 22)).build();

        assertThatNullPointerException()
                .isThrownBy(() -> spec.title(null, "spawn"))
                .withMessageContaining("viewer");
    }

    @Test
    void theBackCallbackIsTheOneTheCallerGave() {
        Player[] seen = new Player[1];
        EditorSpec spec = base(EntityEditorLayout.codeDefault(List.of(11), 22))
                .onBack(player -> seen[0] = player)
                .build();

        spec.onBack().accept(viewer);

        assertThat(seen[0]).isSameAs(viewer);
    }

    @Test
    void theLayoutAndTheValueLineComeBackAsTheyWentIn() {
        EntityEditorLayout layout = new EntityEditorLayout(
                2,
                List.of(3, 4),
                8,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.GRAY_STAINED_GLASS_PANE);
        EditorSpec spec =
                base(layout).valueLore("gui.editor.value").backName("gui.back").build();

        assertThat(spec.layout()).isSameAs(layout);
        assertThat(spec.valueLore()).isEqualTo("gui.editor.value");
        assertThat(spec.backName()).isEqualTo("gui.back");
    }

    /** A builder with every required field set, so each test changes only the one part it is about. */
    private static EditorSpec.Builder base(EntityEditorLayout layout) {
        return EditorSpec.builder()
                .layout(layout)
                .title((player, subject) -> Component.text("title"))
                .valueLore("gui.value")
                .backName("gui.back")
                .properties(subject -> List.of())
                .onBack(player -> {});
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}

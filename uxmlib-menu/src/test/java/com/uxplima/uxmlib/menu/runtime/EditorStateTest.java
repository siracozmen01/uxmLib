package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.property.ClickContext;
import com.uxplima.uxmlib.menu.property.EditableProperty;
import org.junit.jupiter.api.Test;

/**
 * The slot routing of one open editor. An editor re-reads its property list on every draw, which is what makes an
 * edited value show without a reopen, so the routing has to be dropped and rewritten each time rather than accumulated.
 */
class EditorStateTest {

    /** A property that does nothing: these tests are about which slot routes where, never about what an edit does. */
    private static final class NoProperty implements EditableProperty {
        @Override
        public String label() {
            return "label";
        }

        @Override
        public String valueLore(Player viewer) {
            return "";
        }

        @Override
        public Material icon() {
            return Material.STONE;
        }

        @Override
        public void onClick(ClickContext context) {}
    }

    private final EditorState state = new EditorState("an editor spec", "the subject");

    @Test
    void aFreshEditorRoutesNoSlotToAPropertyOrAButton() {
        assertThat(state.propertyAt(0)).isEmpty();
        assertThat(state.buttonAt(0)).isEmpty();
    }

    @Test
    void aPaintedPropertyRoutesAClickBackToTheVeryPropertyItDrew() {
        EditableProperty property = new NoProperty();
        state.recordProperty(11, property);

        assertThat(state.propertyAt(11)).containsSame(property);
        assertThat(state.buttonAt(11))
                .as("a property slot is not also a plain button, or one click would run both")
                .isEmpty();
    }

    @Test
    void aPlainButtonRoutesToTheVeryRunnableItWasRecordedWith() {
        Runnable back = () -> {};
        state.recordButton(49, back);

        assertThat(state.buttonAt(49)).containsSame(back);
        assertThat(state.propertyAt(49)).isEmpty();
    }

    @Test
    void aRedrawDropsEveryRoutingSoAStaleButtonCannotBeClicked() {
        state.recordProperty(11, new NoProperty());
        state.recordButton(49, () -> {});

        state.clearSlots();

        assertThat(state.propertyAt(11)).isEmpty();
        assertThat(state.buttonAt(49)).isEmpty();
    }

    @Test
    void anEditorMayHaveNoSubjectAtAll() {
        assertThat(new EditorState("spec", null).subject()).isNull();
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void theSpecIsCarriedOpaquelyAndAnEditorWithoutOneIsRejected() {
        Object spec = new Object();

        assertThat(new EditorState(spec, null).spec()).isSameAs(spec);
        assertThatThrownBy(() -> new EditorState(null, "subject")).isInstanceOf(NullPointerException.class);
    }
}

package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

/** Pure tests of the clickable-hologram click model, its validation, and live hitbox resizing. */
class ClickableHologramTest {

    @Test
    void clickCarriesPlayerAndButton() {
        // A HologramClick is a simple value; its type tells left from right.
        assertThat(HologramClick.Type.values()).containsExactly(HologramClick.Type.LEFT, HologramClick.Type.RIGHT);
    }

    @Test
    void specOfBuildsLines() {
        // Sanity that HologramSpec.of (used by clickable) keeps the lines.
        HologramSpec spec = HologramSpec.of(java.util.List.of(Component.text("click me")));
        assertThat(spec.lines()).hasSize(1);
    }

    @Test
    void resizePushesTheNewDimensionsToTheLiveInteraction() {
        Interaction box = mock(Interaction.class);
        ClickableHologram clickable = ClickableHologram.of(fakeHologram(), box);

        clickable.resize(2.5f, 3.0f);

        verify(box).setInteractionWidth(2.5f);
        verify(box).setInteractionHeight(3.0f);
    }

    @Test
    void widthAndHeightReadBackFromTheInteraction() {
        Interaction box = mock(Interaction.class);
        when(box.getInteractionWidth()).thenReturn(1.5f);
        when(box.getInteractionHeight()).thenReturn(2.0f);
        ClickableHologram clickable = ClickableHologram.of(fakeHologram(), box);

        assertThat(clickable.width()).isEqualTo(1.5f);
        assertThat(clickable.height()).isEqualTo(2.0f);
    }

    @Test
    void resizeRejectsNonPositiveDimensions() {
        ClickableHologram clickable = ClickableHologram.of(fakeHologram(), mock(Interaction.class));
        assertThatThrownBy(() -> clickable.resize(0f, 2f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clickable.resize(2f, -1f)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Hologram fakeHologram() {
        TextDisplay display = mock(TextDisplay.class);
        Hologram hologram = mock(Hologram.class);
        when(hologram.entity()).thenReturn(display);
        return hologram;
    }
}

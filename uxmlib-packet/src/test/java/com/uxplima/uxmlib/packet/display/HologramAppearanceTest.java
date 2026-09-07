package com.uxplima.uxmlib.packet.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The look of a packet hologram: a value that cannot be changed from underneath the driver holding it. */
class HologramAppearanceTest {

    @Test
    @DisplayName("the default look faces the viewer, carries no panel and is fully opaque")
    void theDefaultLook() {
        HologramAppearance appearance = HologramAppearance.defaults();

        assertThat(appearance.billboard()).isEqualTo(Display.Billboard.CENTER);
        assertThat(appearance.alignment()).isEqualTo(TextDisplay.TextAlignment.CENTER);
        assertThat(appearance.backgroundArgb()).isZero();
        assertThat(appearance.textOpacity()).isEqualTo(HologramAppearance.FULL_OPACITY);
        assertThat(appearance.scale()).isEqualTo(new Vector3f(1f, 1f, 1f));
    }

    @Test
    @DisplayName("a caller cannot move a stored appearance by editing the vector it handed in or read back")
    void theVectorsAreCopied() {
        Vector3f translation = new Vector3f(0f, 1f, 0f);
        HologramAppearance appearance = HologramAppearance.defaults().withTranslation(0f, 1f, 0f);

        translation.y = 99f;
        appearance.translation().y = 99f;

        assertThat(appearance.translation()).isEqualTo(new Vector3f(0f, 1f, 0f));
    }

    @Test
    @DisplayName("each wither changes one thing and leaves the rest")
    void aWitherChangesOneThing() {
        HologramAppearance appearance = HologramAppearance.defaults()
                .withBackground(Color.fromARGB(128, 0, 0, 0))
                .withSeeThrough(true)
                .withLineWidth(80);

        assertThat(appearance.backgroundArgb())
                .isEqualTo(Color.fromARGB(128, 0, 0, 0).asARGB());
        assertThat(appearance.seeThrough()).isTrue();
        assertThat(appearance.lineWidth()).isEqualTo(80);
        assertThat(appearance.textShadow()).isFalse();
        assertThat(appearance.billboard()).isEqualTo(Display.Billboard.CENTER);
    }

    @Test
    @DisplayName("an opacity outside 0 to 255 is refused")
    void anImpossibleOpacityIsRefused() {
        assertThatThrownBy(() -> HologramAppearance.defaults().withTextOpacity(256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textOpacity");
    }

    @Test
    @DisplayName("a negative wrap width is refused")
    void aNegativeLineWidthIsRefused() {
        assertThatThrownBy(() -> HologramAppearance.defaults().withLineWidth(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineWidth");
    }
}

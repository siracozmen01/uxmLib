package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.entity.Display;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

/** Pure tests of the hologram builder's accumulated spec — live spawning is integration-only. */
class HologramSpecTest {

    @Test
    void accumulatesLinesAndDefaultsToCenterBillboard() {
        HologramSpec spec = Holograms.builder()
                .line(Component.text("one"))
                .line(Component.text("two"))
                .spec();

        assertThat(spec.lines()).hasSize(2);
        assertThat(spec.billboard()).isEqualTo(Display.Billboard.CENTER);
        assertThat(spec.seeThrough()).isFalse();
    }

    @Test
    void joinsLinesWithNewlines() {
        HologramSpec spec = Holograms.builder()
                .line(Component.text("top"))
                .line(Component.text("bottom"))
                .spec();

        assertThat(Text.plain(spec.asText())).isEqualTo("top\nbottom");
    }

    @Test
    void honoursBillboardAndSeeThrough() {
        HologramSpec spec = Holograms.builder()
                .line(Component.text("x"))
                .billboard(Display.Billboard.FIXED)
                .seeThrough(true)
                .spec();

        assertThat(spec.billboard()).isEqualTo(Display.Billboard.FIXED);
        assertThat(spec.seeThrough()).isTrue();
    }

    @Test
    void requiresAtLeastOneLine() {
        assertThatThrownBy(() -> Holograms.builder().spec()).isInstanceOf(IllegalArgumentException.class);
    }
}

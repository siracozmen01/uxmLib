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
        assertThat(spec.appearance().billboard()).isEqualTo(Display.Billboard.CENTER);
        assertThat(spec.appearance().seeThrough()).isFalse();
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

        assertThat(spec.appearance().billboard()).isEqualTo(Display.Billboard.FIXED);
        assertThat(spec.appearance().seeThrough()).isTrue();
    }

    @Test
    void stylingRoundTripsThroughTheSpec() {
        // The old bug: glow (and other styling) was applied at spawn but dropped from the spec. Now the
        // spec captures every styling field, so what you build is what you get.
        HologramSpec spec = Holograms.builder()
                .line(Component.text("styled"))
                .glow(org.bukkit.Color.RED)
                .background(org.bukkit.Color.fromARGB(128, 0, 0, 0))
                .textShadow(true)
                .lineWidth(120)
                .viewRange(2.0f)
                .spec();

        Appearance look = spec.appearance();
        assertThat(look.glow()).isEqualTo(org.bukkit.Color.RED);
        assertThat(look.background()).isEqualTo(org.bukkit.Color.fromARGB(128, 0, 0, 0));
        assertThat(look.textShadow()).isTrue();
        assertThat(look.lineWidth()).isEqualTo(120);
        assertThat(look.viewRange()).isEqualTo(2.0f);
    }

    @Test
    void requiresAtLeastOneLine() {
        assertThatThrownBy(() -> Holograms.builder().spec()).isInstanceOf(IllegalArgumentException.class);
    }
}

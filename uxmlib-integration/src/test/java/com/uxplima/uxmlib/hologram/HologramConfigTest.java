package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.entity.Display;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** Pure tests of building a hologram spec from a HOCON config node — no server. */
class HologramConfigTest {

    private static CommentedConfigurationNode parse(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new java.io.StringReader(hocon)))
                .build()
                .load();
    }

    @Test
    void readsLinesAndAppearance() throws Exception {
        String hocon =
                """
                lines = [ "<gold>Spawn", "<gray>Welcome" ]
                appearance {
                  billboard = FIXED
                  glow = "#ff5555"
                  scale = 1.5
                  textShadow = true
                }
                """;
        HologramSpec spec = HologramConfig.load(parse(hocon));

        assertThat(spec.lines()).hasSize(2);
        assertThat(Text.plain(spec.lines().get(0))).isEqualTo("Spawn");
        Appearance look = spec.appearance();
        assertThat(look.billboard()).isEqualTo(Display.Billboard.FIXED);
        assertThat(look.glow()).isEqualTo(org.bukkit.Color.fromRGB(0xff5555));
        assertThat(look.textShadow()).isTrue();
        assertThat(java.util.Objects.requireNonNull(look.transform()).scaleX()).isEqualTo(1.5f);
    }

    @Test
    void defaultsAppearanceWhenAbsent() throws Exception {
        HologramSpec spec = HologramConfig.load(parse("lines = [ \"hi\" ]"));
        assertThat(spec.appearance().billboard()).isEqualTo(Display.Billboard.CENTER);
        assertThat(spec.appearance().transform()).isNull();
    }

    @Test
    void rejectsNoLines() throws Exception {
        assertThatThrownBy(() -> HologramConfig.load(parse("appearance { billboard = CENTER }")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadColour() throws Exception {
        String hocon = "lines = [ \"x\" ]\nappearance { glow = \"nothex\" }";
        assertThatThrownBy(() -> HologramConfig.load(parse(hocon))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownBillboard() throws Exception {
        String hocon = "lines = [ \"x\" ]\nappearance { billboard = SIDEWAYS }";
        assertThatThrownBy(() -> HologramConfig.load(parse(hocon))).isInstanceOf(IllegalArgumentException.class);
    }
}

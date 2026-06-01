package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import net.kyori.adventure.bossbar.BossBar;

import com.uxplima.uxmlib.config.ConfigException;
import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the {@link Message} Configurate serializer reads each channel from HOCON and round-trips back
 * through a real file, so an admin retargets a message by editing {@code type} alone.
 */
class MessageSerializerTest {

    private HoconConfig configOf(Path file, String hocon) throws Exception {
        Files.writeString(file, hocon);
        return HoconConfig.load(file, MessageSerializer.collection());
    }

    @Test
    void readsABareStringAsChat(@TempDir Path dir) throws Exception {
        HoconConfig config = configOf(dir.resolve("m.conf"), "msg = \"<green>Hello\"\n");

        Message message = config.getNode("msg", Message.class, new Message.Silent());

        assertThat(message).isEqualTo(new Message.Chat("<green>Hello"));
    }

    @Test
    void readsATypedTitleSection(@TempDir Path dir) throws Exception {
        HoconConfig config = configOf(
                dir.resolve("m.conf"),
                "msg { type = title, text = \"<gold>Hi\", subtitle = \"<gray>welcome\", fadeIn = 250, stay = 2000, fadeOut = 250 }\n");

        Message message = config.getNode("msg", Message.class, new Message.Silent());

        assertThat(message)
                .isEqualTo(new Message.TitleText(
                        "<gold>Hi",
                        "<gray>welcome",
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250)));
    }

    @Test
    void readsAnActionBarSection(@TempDir Path dir) throws Exception {
        HoconConfig config = configOf(dir.resolve("m.conf"), "msg { type = actionbar, text = \"<aqua>ping\" }\n");

        assertThat(config.getNode("msg", Message.class, new Message.Silent()))
                .isEqualTo(new Message.ActionBar("<aqua>ping"));
    }

    @Test
    void readsABossBarSectionWithColourAndOverlay(@TempDir Path dir) throws Exception {
        HoconConfig config = configOf(
                dir.resolve("m.conf"),
                "msg { type = bossbar, text = \"<red>boss\", progress = 0.5, color = red, overlay = notched_10 }\n");

        assertThat(config.getNode("msg", Message.class, new Message.Silent()))
                .isEqualTo(new Message.BossBarText("<red>boss", 0.5f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
    }

    @Test
    void readsSilent(@TempDir Path dir) throws Exception {
        HoconConfig config = configOf(dir.resolve("m.conf"), "msg { type = silent }\n");

        assertThat(config.getNode("msg", Message.class, new Message.Chat("x"))).isEqualTo(new Message.Silent());
    }

    @Test
    void rejectsAnUnknownType(@TempDir Path dir) throws Exception {
        HoconConfig config = configOf(dir.resolve("m.conf"), "msg { type = banner, text = \"x\" }\n");

        assertThatThrownBy(() -> config.getNode("msg", Message.class, new Message.Silent()))
                .isInstanceOf(ConfigException.class);
    }

    @Test
    void titleRoundTripsThroughSaveAndReload(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("m.conf");
        HoconConfig config = configOf(file, "msg { type = title, text = \"<gold>Hi\", stay = 1500 }\n");

        Message original = config.getNode("msg", Message.class, new Message.Silent());
        config.root().node("roundtrip").set(Message.class, original);
        config.save();

        HoconConfig reread = HoconConfig.load(file, MessageSerializer.collection());
        assertThat(reread.getNode("roundtrip", Message.class, new Message.Silent()))
                .isEqualTo(original);
    }

    @Test
    void bossBarRoundTripsThroughSave(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("m.conf");
        HoconConfig config = HoconConfig.load(file, MessageSerializer.collection());

        Message original = new Message.BossBarText("<red>x", 0.25f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_20);
        config.root().node("bar").set(Message.class, original);
        config.save();

        HoconConfig reread = HoconConfig.load(file, MessageSerializer.collection());
        assertThat(reread.getNode("bar", Message.class, new Message.Silent())).isEqualTo(original);
    }
}

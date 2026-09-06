package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.render.ItemRenderer;
import com.uxplima.uxmlib.menu.render.MenuRenderer;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.spec.Ref;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.sound.AudioExperience;

/**
 * The five verbs a menu file may write in any plugin. They are driven here through the facade rather than through a
 * click, because that is the same dispatcher a click uses and it needs no gesture to arrange.
 */
class MenuBasicsTest {

    /** A catalogue that hands every key straight back, so nothing here depends on a message file. */
    private static final class PlainText implements GuiText {

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text(key);
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** A command that records who ran it, so the command verb is provable without a plugin behind it. */
    private static final class Echo extends Command {

        private final List<String> ranBy = new ArrayList<>();

        private Echo() {
            super("echo");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            ranBy.add(sender.getName());
            return true;
        }
    }

    private MenuBindings bindings;

    private Menus menus;

    private PlayerMock viewer;

    private Echo echo;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin();
        viewer = MockBukkit.getMock().addPlayer();
        bindings = new MenuBindings();
        MenuRenderer renderer = new MenuRenderer(
                new ItemRenderer(new PlainText(), Theme::defaults, bindings.placeholders()), bindings.conditions());
        menus = new Menus(
                renderer, new SameThreadScheduler(), bindings.lists(), null, bindings.actions(), bindings.conditions());
        MenuBasics.register(bindings, menus);
        echo = new Echo();
        MockBukkit.getMock().getCommandMap().register("uxmlib", echo);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void run(String line) {
        menus.execute(viewer, Ref.parse(line));
    }

    private void register(String id, String hocon) {
        menus.registerSpec(id, new MenuSpecLoader().parse(hocon));
    }

    /** MockBukkit hands back a null top inventory once a window is closed, so every read of one goes through here. */
    private @Nullable Object holderOfOpenWindow() {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder();
    }

    private AudioExperience onlySound() {
        assertThat(viewer.getHeardSounds()).hasSize(1);
        return viewer.getHeardSounds().get(0);
    }

    // -- close ---------------------------------------------------------------------------------------------------

    @Test
    void closeShutsTheWindowTheViewerIsLookingAt() {
        register("hub", "rows = 3");
        menus.open(viewer, "hub", null);
        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);

        run("close");

        assertThat(holderOfOpenWindow() instanceof MenuHolder).isFalse();
    }

    // -- open ----------------------------------------------------------------------------------------------------

    @Test
    void openShowsTheMenuTheLineNames() {
        register("hub", "rows = 3");

        run("open:hub");

        assertThat(holderOfOpenWindow()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void spaceAroundTheMenuNameIsNotPartOfIt() {
        register("hub", "rows = 3");

        run("open: hub ");

        assertThat(holderOfOpenWindow())
                .as("a file written with a space after the colon reads the same as one without")
                .isInstanceOf(MenuHolder.class);
    }

    // -- command -------------------------------------------------------------------------------------------------

    @Test
    void commandRunsAsTheViewerAndNotAsTheConsole() {
        run("command:echo");

        assertThat(echo.ranBy).containsExactly(viewer.getName());
    }

    // -- message -------------------------------------------------------------------------------------------------

    @Test
    void messageReachesTheViewerWithItsTagsRead() {
        run("message:<red>hello");

        Component said = viewer.nextComponentMessage();
        assertThat(PlainTextComponentSerializer.plainText().serialize(said)).isEqualTo("hello");
        assertThat(said.color()).isEqualTo(NamedTextColor.RED);
    }

    // -- sound ---------------------------------------------------------------------------------------------------

    @Test
    void soundPlaysTheKeyTheFileNamesAtTheVolumeAndPitchItAsksFor() {
        run("sound:block.note_block.pling 0.6 1.5");

        AudioExperience heard = onlySound();
        assertThat(heard.getSound()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(heard.getVolume()).isEqualTo(0.6F);
        assertThat(heard.getPitch()).isEqualTo(1.5F);
    }

    @Test
    void theConstantSpellingNamesTheSameSoundAsTheKey() {
        run("sound:BLOCK_NOTE_BLOCK_PLING");

        assertThat(onlySound().getSound())
                .as("an operator who copied the name out of the Bukkit API wrote the same sound")
                .isEqualTo("minecraft:block.note_block.pling");
    }

    @Test
    void aLineThatNamesOnlyASoundPlaysItWhole() {
        run("sound:block.note_block.pling");

        AudioExperience heard = onlySound();
        assertThat(heard.getVolume()).isEqualTo(1.0F);
        assertThat(heard.getPitch()).isEqualTo(1.0F);
    }

    @Test
    void aVolumeThatIsNotANumberIsOneRatherThanAThrownClick() {
        run("sound:block.note_block.pling loud soft");

        AudioExperience heard = onlySound();
        assertThat(heard.getVolume()).isEqualTo(1.0F);
        assertThat(heard.getPitch()).isEqualTo(1.0F);
    }

    @Test
    void aSoundThisServerDoesNotHaveIsSilence() {
        run("sound:NO_SUCH_SOUND_AT_ALL");

        assertThat(viewer.getHeardSounds()).isEmpty();
    }
}

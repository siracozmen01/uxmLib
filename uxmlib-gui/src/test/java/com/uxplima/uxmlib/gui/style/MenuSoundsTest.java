package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The four sounds: what they are unconfigured, what a file changes, and how an operator turns one off. */
class MenuSoundsTest {

    @Test
    void theShippedSetIsTheOneTheClientIsKnownToPlay() {
        MenuSounds sounds = MenuSounds.defaults();

        assertThat(sounds.open().name().asString()).isEqualTo("minecraft:item.book.page_turn");
        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(sounds.page().name().asString()).isEqualTo("minecraft:item.book.page_turn");
        assertThat(sounds.denied().name().asString()).isEqualTo("minecraft:block.note_block.bass");
        assertThat(sounds.click().volume()).isEqualTo(0.6f);
    }

    /**
     * The page turn and the open share a key and part on pitch. They are one tone in the shipped set and two entries in
     * the file, which is the whole reason page is its own component: an operator who silences the open sound expects a
     * quiet open, not a menu whose pages stop answering.
     */
    @Test
    void theOpenAndThePageShareAKeyAndStillPartOnPitch() {
        MenuSounds sounds = MenuSounds.defaults();

        assertThat(sounds.page().name()).isEqualTo(sounds.open().name());
        assertThat(sounds.page().pitch()).isNotEqualTo(sounds.open().pitch());
    }

    @Test
    void silencingTheOpenLeavesThePageAudible(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { open { name = \"\" } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.open().volume()).isZero();
        assertThat(sounds.page().volume()).isEqualTo(0.7f);
    }

    @Test
    void aFileChangesTheSoundsItNamesAndKeepsTheRest(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { click { name = \"ui.button.click\", volume = 0.2 } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:ui.button.click");
        assertThat(sounds.click().volume()).isEqualTo(0.2f);
        assertThat(sounds.open().name().asString()).isEqualTo("minecraft:item.book.page_turn");
    }

    @Test
    void aFileRetunesThePageLikeAnyOtherSound(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { page { name = \"ui.button.click\", pitch = 1.4 } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.page().name().asString()).isEqualTo("minecraft:ui.button.click");
        assertThat(sounds.page().pitch()).isEqualTo(1.4f);
    }

    /** An empty name is how a server turns one sound off, and silence has to be exactly silent. */
    @Test
    void anEmptyNameIsSilence(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { open { name = \"\" } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.open().volume()).isZero();
    }

    /** A name that is the same sound in the case the server prints is the same sound. That much is pure case. */
    @Test
    void aKeyWrittenInUpperCaseIsTheSameKey(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { click { name = \"MINECRAFT:BLOCK.BELL.USE\" } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:block.bell.use");
    }

    /**
     * The constant spelling falls back to the shipped tone rather than being translated, because it cannot be
     * translated here. BLOCK_ANVIL_LAND is block.anvil.land and BLOCK_NOTE_BLOCK_PLING is block.note_block.pling:
     * some of those underscores are dots and some are not, and only the sound registry knows which. Lower-casing
     * alone yields a well formed key that names no sound, which plays silence with nothing to search for. This record
     * reads no registry on purpose, so the honest answer is the wrong click an operator can hear and report.
     */
    @Test
    void theConstantSpellingFallsBackRatherThanBecomingAKeyThatNamesNoSound(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { click { name = \"BLOCK_ANVIL_LAND\", volume = 0.2 } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(sounds.click().volume())
                .as("the tone falls back, the operator's own volume and pitch still apply")
                .isEqualTo(0.2f);
    }

    @Test
    void surroundingSpaceInANameIsNotPartOfTheKey(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { click { name = \"  ui.button.click  \" } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:ui.button.click");
    }

    /**
     * A name the key grammar cannot hold falls back to the shipped tone. A typo in one line of a configuration file
     * must not stop a menu from opening, and a menu that opens with the wrong click is a report an operator can act on.
     */
    @Test
    void aNameTheKeyGrammarRefusesFallsBackToTheShippedTone(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.conf");
        Files.writeString(file, "menu { sounds { click { name = \"ui button click!\", volume = 0.2 } } }\n");

        MenuSounds sounds = MenuSounds.from(HoconConfig.load(file), "menu.sounds");

        assertThat(sounds.click().name().asString()).isEqualTo("minecraft:block.note_block.pling");
        assertThat(sounds.click().volume()).isEqualTo(0.2f);
    }

    /**
     * The two readers of a sound name disagree on the constant spelling, and this pins the disagreement rather
     * than the agreement. {@link SoundNames} has the server to ask and resolves a constant; this record is built
     * from a file at startup with no registry at hand, so it refuses one and falls back to the shipped tone.
     * Their character class is identical today, which is exactly why it is worth asking: a shared helper would
     * weld two questions into one, and a widening on either side would move the other without saying so.
     */
    @Test
    void everyConstantTheServerResolvesIsOneThisRecordRefuses(@TempDir Path dir) throws Exception {
        for (String constant : List.of("ITEM_BOOK_PAGE_TURN", "BLOCK_NOTE_BLOCK_PLING", "UI_BUTTON_CLICK")) {
            assertThat(SoundNames.key(constant))
                    .as("the server knows this constant: " + constant)
                    .isPresent();

            assertThat(clickOf(dir, constant))
                    .as("this record cannot resolve it without a registry, so it refuses: " + constant)
                    .isEqualTo("minecraft:block.note_block.pling");
        }
    }

    /**
     * The two gates coincide in shape and not in meaning, and this is where that shows. A name spelled like a
     * constant that names no sound is refused by both, for two different reasons: the server has nothing to
     * answer with, and this record would not have asked it anyway.
     */
    @Test
    void aConstantShapedNameThatNamesNoSoundIsRefusedByBoth(@TempDir Path dir) throws Exception {
        assertThat(SoundNames.key("A1_B2")).isEmpty();
        assertThat(clickOf(dir, "A1_B2")).isEqualTo("minecraft:block.note_block.pling");
    }

    /** The click sound a configuration naming {@code written} ends up with. */
    private static String clickOf(Path dir, String written) throws Exception {
        Path file = dir.resolve(written + ".conf");
        Files.writeString(file, "menu { sounds { click { name = \"" + written + "\" } } }\n");
        return MenuSounds.from(HoconConfig.load(file), "menu.sounds")
                .click()
                .name()
                .asString();
    }

    @Test
    void aMissingFileLeavesEverySoundAtItsShippedValue(@TempDir Path dir) {
        MenuSounds sounds = MenuSounds.from(HoconConfig.load(dir.resolve("absent.conf")), "menu.sounds");

        assertThat(sounds).isEqualTo(MenuSounds.defaults());
    }

    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    @Test
    void aSetWithNoPageIsRefusedRatherThanPlayingNothingLater() {
        MenuSounds shipped = MenuSounds.defaults();

        assertThatNullPointerException()
                .isThrownBy(() -> new MenuSounds(shipped.open(), shipped.click(), null, shipped.denied()))
                .withMessage("page");
    }
}

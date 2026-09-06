package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The one place the constant spelling of a sound is honoured. An operator copies {@code BLOCK_NOTE_BLOCK_PLING}
 * out of the API and writes it in a menu file, and only the server knows what that is called this release.
 *
 * <p>Getting this wrong is silent by nature: a click that plays nothing sounds like a click on an item that has
 * no sound, so nobody reports it. That is why both spellings are asserted against the same key here rather than
 * each against itself.
 */
class SoundNamesTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** The spelling the server itself uses is taken as written, with no registry walk at all. */
    @Test
    void aKeyIsTakenAsItIsWritten() {
        assertThat(SoundNames.key("block.note_block.pling"))
                .hasValueSatisfying(key -> assertThat(key.asString()).isEqualTo("minecraft:block.note_block.pling"));
    }

    /** The constant an operator copies out of the API names the same sound as its key does. */
    @Test
    void aConstantResolvesToTheKeyOfTheSameSound() {
        assertThat(SoundNames.key("BLOCK_NOTE_BLOCK_PLING")).isEqualTo(SoundNames.key("block.note_block.pling"));
    }

    /** The fold runs dots to underscores, so a name with several of them is one sound and not a prefix match. */
    @Test
    void everyDotOfTheKeyIsOneUnderscoreOfTheConstant() {
        assertThat(SoundNames.key("ITEM_BOOK_PAGE_TURN"))
                .hasValueSatisfying(key -> assertThat(key.asString()).isEqualTo("minecraft:item.book.page_turn"));
    }

    /** A name written with space around it is the same name. An operator's formatting is not part of it. */
    @Test
    void spaceAroundTheNameIsNotPartOfIt() {
        assertThat(SoundNames.key("  BLOCK_NOTE_BLOCK_PLING  ")).isEqualTo(SoundNames.key("BLOCK_NOTE_BLOCK_PLING"));
    }

    /**
     * A sound this server does not have answers empty rather than a key that plays nothing. The caller turns
     * that into silence on purpose, which is what a menu does with a sound a version dropped.
     */
    @Test
    void aConstantTheServerDoesNotHaveIsEmpty() {
        assertThat(SoundNames.key("BLOCK_NOTE_BLOCK_NOTHING_LIKE_THIS")).isEmpty();
    }

    /** Nothing written is nothing named, and it is not the first sound in the registry either. */
    @Test
    void anEmptyNameNamesNoSound() {
        assertThat(SoundNames.key("")).isEmpty();
        assertThat(SoundNames.key("   ")).isEmpty();
    }
}

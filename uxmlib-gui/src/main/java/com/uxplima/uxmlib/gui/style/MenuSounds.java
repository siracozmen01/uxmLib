package com.uxplima.uxmlib.gui.style;

import java.util.Locale;
import java.util.Objects;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The four sounds a menu plays: opening, a click that acts, turning a page, and a click that is refused.
 *
 * <p>A refusal answers with a low note rather than with silence, because a button that does nothing and says
 * nothing reads as a broken menu. The shipped volumes sit between 0.5 and 0.7: loud enough to feel, quiet
 * enough to live with.
 *
 * <p>A sound is named by its vanilla key ({@code item.book.page_turn}) rather than by a Bukkit constant,
 * because the key is what the client plays and it does not move with the server software. A name the client
 * does not know plays nothing, which is why the shipped values are the tested ones. An empty name in the file
 * is how an operator turns one sound off.
 *
 * <p>{@code page} is separate from {@code open} on purpose, even though both ship the page-turn key: an operator
 * silencing the sound a menu makes when it opens should not thereby silence every page they turn inside it.
 */
public record MenuSounds(Sound open, Sound click, Sound page, Sound denied) {

    private static final String OPEN_KEY = "item.book.page_turn";
    private static final String CLICK_KEY = "block.note_block.pling";
    private static final String PAGE_KEY = "item.book.page_turn";
    private static final String DENIED_KEY = "block.note_block.bass";

    public MenuSounds {
        Objects.requireNonNull(open, "open");
        Objects.requireNonNull(click, "click");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(denied, "denied");
    }

    /** The shipped set, used when the file says nothing. */
    public static MenuSounds defaults() {
        return new MenuSounds(
                sound(OPEN_KEY, OPEN_KEY, 0.7f, 1.2f),
                sound(CLICK_KEY, CLICK_KEY, 0.6f, 1.5f),
                sound(PAGE_KEY, PAGE_KEY, 0.7f, 1.0f),
                sound(DENIED_KEY, DENIED_KEY, 0.6f, 0.9f));
    }

    /**
     * The set in {@code config} under {@code base}: {@code "menu.sounds"} by convention. Each sound keeps
     * its shipped value until the file names another one.
     */
    public static MenuSounds from(HoconConfig config, String base) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(base, "base");
        return new MenuSounds(
                read(config, base + ".open", OPEN_KEY, 0.7f, 1.2f),
                read(config, base + ".click", CLICK_KEY, 0.6f, 1.5f),
                read(config, base + ".page", PAGE_KEY, 0.7f, 1.0f),
                read(config, base + ".denied", DENIED_KEY, 0.6f, 0.9f));
    }

    private static Sound read(HoconConfig config, String path, String key, float volume, float pitch) {
        String name = config.getString(path + ".name", key);
        if (name.isBlank()) {
            // An empty name is how an operator turns one sound off. A volume of zero plays nothing.
            return sound(key, key, 0f, pitch);
        }
        float configured = (float) config.getDouble(path + ".volume", volume);
        return sound(name, key, configured, (float) config.getDouble(path + ".pitch", pitch));
    }

    /**
     * The sound {@code name} asks for, falling back to the shipped {@code fallback} key when the file names something
     * this record cannot turn into a key. A well formed key the client does not know still plays nothing, which is the
     * documented behaviour and is unchanged; this covers the two cases where the name never becomes a usable key at
     * all.
     *
     * <p>The name is trimmed and lower-cased first, because the key grammar holds lower case only and an operator who
     * writes {@code MINECRAFT:BLOCK.BELL.USE} is writing the same sound in the case the server prints. That much is
     * pure case, and {@link com.uxplima.uxmlib.common.Sounds} already reads a name that way.
     *
     * <p>The constant spelling, {@code BLOCK_NOTE_BLOCK_PLING}, is a different question and this record answers it by
     * refusing rather than guessing. It cannot be translated by string work: the key is
     * {@code block.note_block.pling}, so some of those underscores are dots and one is an underscore, and nothing in
     * the constant says which. Replacing every underscore with a dot gives {@code block.note.block.pling}, and lower-
     * casing alone gives {@code block_note_block_pling}: both are well formed keys, both name no sound, and both play
     * silence with no diagnostic. Only the sound registry can answer it, which is why {@link com.uxplima.uxmlib.gui.config.MenuAction} defers a
     * constant until the moment it plays. Nothing here reads a registry, because that is what lets a configuration
     * file be tested with no server under it, so the constant form falls back to the shipped tone: an operator hears
     * the wrong click and has something to report, rather than hearing nothing and having nothing to search for.
     */
    private static Sound sound(String name, String fallback, float volume, float pitch) {
        return Sound.sound(keyOrFallback(name, fallback), Sound.Source.MASTER, volume, pitch);
    }

    private static Key keyOrFallback(String name, String fallback) {
        String trimmed = name.trim();
        if (isConstant(trimmed)) {
            return Key.key(fallback);
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        return Key.parseable(lowered) ? Key.key(lowered) : Key.key(fallback);
    }

    /**
     * Whether the name is the constant form: upper case letters, digits and underscores only.
     *
     * <p>{@link com.uxplima.uxmlib.gui.config.MenuAction} spells the same character class, and the copy is deliberate.
     * They are not one rule in two places. That one is an accept gate, asking whether a string may be stored as a
     * sound name at all; this one is a refuse gate, asking whether a name is one this record provably cannot resolve
     * without a registry. The two coincide today by arithmetic rather than by meaning, and a shared helper would weld
     * them: teach that one a third accepted spelling and this one would silently start refusing it in the same edit,
     * with nothing saying that was a decision.
     *
     * <p>What holds the two apart is not this note. MenuSoundsTest runs a table of constant spellings through both
     * public seams and asserts they disagree, so widening either side turns that test red and the widening has to say
     * what this record does with the new spelling.
     */
    private static boolean isConstant(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int at = 0; at < name.length(); at++) {
            char letter = name.charAt(at);
            boolean allowed = letter == '_' || (letter >= 'A' && letter <= 'Z') || (letter >= '0' && letter <= '9');
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}

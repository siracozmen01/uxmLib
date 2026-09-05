package com.uxplima.uxmlib.gui.style;

import java.util.Locale;
import java.util.Objects;

import net.kyori.adventure.key.InvalidKeyException;
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
     * that is not a key at all. A well formed key the client does not know still plays nothing, which is the documented
     * behaviour and is unchanged; this covers the different case of a name Adventure refuses to parse.
     *
     * <p>The name is lower-cased first, because an operator who writes {@code BLOCK_NOTE_BLOCK_PLING} is writing the
     * form the server prints rather than making a mistake they would recognise as one. This library already tolerates
     * that spelling elsewhere, so throwing on it here was the same operator meeting two answers to one question.
     *
     * <p>Anything still unparseable falls back rather than propagating: a typo that plays the shipped sound is a shrug,
     * and a typo that stops the plugin loading is an outage. Nothing here reads a registry, so this stays loadable with
     * no server under it, which is what lets the configuration be tested at all.
     */
    private static Sound sound(String name, String fallback, float volume, float pitch) {
        return Sound.sound(keyOrFallback(name, fallback), Sound.Source.MASTER, volume, pitch);
    }

    private static Key keyOrFallback(String name, String fallback) {
        try {
            return Key.key(name.trim().toLowerCase(Locale.ROOT));
        } catch (InvalidKeyException malformed) {
            return Key.key(fallback);
        }
    }
}

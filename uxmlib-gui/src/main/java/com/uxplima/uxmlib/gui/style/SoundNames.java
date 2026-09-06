package com.uxplima.uxmlib.gui.style;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Registry;

import net.kyori.adventure.key.Key;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The two ways an operator writes a sound, and the one key both of them name.
 *
 * <p>A file may say {@code block.note_block.pling} or {@code BLOCK_NOTE_BLOCK_PLING}. The first is the key the
 * server itself uses and is taken as written. The second is the constant an operator copies out of the Bukkit
 * API, and only the server knows what it is called this release, so it is resolved by asking: every sound the
 * server has is walked once and its key folded into the constant spelling, dots to underscores and upper case.
 * The fold runs forwards, from the key the server gave to the constant an operator wrote, so it is exact:
 * folding the other way would have to guess which underscores were dots.
 *
 * <p>A name the server does not have answers empty. A menu that names a sound this version dropped is silent
 * on that click and never a broken one.
 *
 * <p>This is where the constant spelling is honoured, and it is deliberately not where a sound is stored.
 * {@link MenuSounds} reads its names out of a configuration file at startup and refuses the constant form,
 * because it holds a name rather than resolving one and has no registry at hand when it is built. A caller
 * that has a player to play to has the server too, and asks here.
 */
@NullMarked
public final class SoundNames {

    private SoundNames() {}

    /**
     * The key {@code written} names, in either spelling, or empty when this server has no such sound.
     *
     * <p>Walks the sound registry only for the constant form. A key is answered without asking the server at
     * all, which is the spelling every file this library ships uses.
     */
    public static Optional<Key> key(String written) {
        Objects.requireNonNull(written, "written");
        String name = written.strip();
        if (name.isEmpty()) {
            return Optional.empty();
        }
        if (Key.parseable(name)) {
            return Optional.of(Key.key(name));
        }
        return constant(name);
    }

    /** The key of the sound this server calls {@code name}, or empty when it calls nothing that. */
    private static Optional<Key> constant(String name) {
        for (org.bukkit.Sound sound : Registry.SOUND_EVENT) {
            @Nullable Key key = Registry.SOUND_EVENT.getKey(sound);
            if (key != null && name.equals(key.value().replace('.', '_').toUpperCase(Locale.ROOT))) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }
}

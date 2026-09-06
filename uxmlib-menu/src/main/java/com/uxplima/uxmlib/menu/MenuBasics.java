package com.uxplima.uxmlib.menu;

import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.gui.style.SoundNames;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.NullMarked;

/**
 * The five actions that mean the same thing in every menu of every plugin, registered on one call.
 *
 * <p>{@code close}, {@code open:<menu>}, {@code command:<line>}, {@code message:<line>} and
 * {@code sound:<name> <volume> <pitch>}. They are here so that a plugin which only wants a working menu file
 * does not write them again, and so that an operator who has written one menu can write any of them.
 *
 * <p>These are mechanisms and not a look. Nothing here decides a colour, a word, or a layout: the file says
 * what to run and the plugin says what its own verbs mean. That is why the library may hold them.
 *
 * <p>Registration is a call and never automatic. A plugin that wants its own {@code sound} or its own
 * {@code command}, with a permission gate or a different vocabulary, simply does not make this call and
 * registers its own under the same names.
 *
 * <p>Every verb runs on the thread of the click, which the engine has already put on the viewer's entity
 * thread. Nothing here reaches for a scheduler, so this is safe on Folia.
 */
@NullMarked
public final class MenuBasics {

    /** What a volume and a pitch are when the file writes neither, and when it writes something that is not a number. */
    private static final float DEFAULT = 1.0F;

    private MenuBasics() {}

    /**
     * Register the four verbs that need nothing but the viewer: {@code close}, {@code command},
     * {@code message} and {@code sound}.
     *
     * <p>{@code open} is not among them, because opening a menu needs the engine that holds the menus. A
     * plugin with more than one window uses {@link #register(MenuBindings, Menus)} instead.
     */
    public static void register(MenuBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        bindings.action("close", ctx -> ctx.player().closeInventory());
        bindings.action("command", ctx -> ctx.player().performCommand(ctx.arg()));
        bindings.action("message", ctx -> ctx.player().sendMessage(Text.mini(ctx.arg())));
        bindings.action("sound", MenuBasics::sound);
    }

    /** The same four verbs, and {@code open:<menu>} on top of them. */
    public static void register(MenuBindings bindings, Menus menus) {
        Objects.requireNonNull(menus, "menus");
        register(bindings);
        bindings.action("open", ctx -> menus.open(ctx.player(), ctx.arg().strip(), null));
    }

    /**
     * Play what the line names, to the viewer alone.
     *
     * <p>A name this server does not have is silence. So is a volume or a pitch that is not a number, which
     * falls back to one rather than throwing: this runs under a player's cursor, and a click that throws is
     * worse than a click that is quiet. The line was read from a file that an operator may edit while the
     * server runs, so there is no earlier moment at which to refuse it.
     */
    private static void sound(MenuActionContext ctx) {
        String[] parts = ctx.arg().strip().split("\\s+");
        SoundNames.key(parts[0]).ifPresent(key -> play(ctx.player(), key, number(parts, 1), number(parts, 2)));
    }

    private static void play(Player viewer, Key key, float volume, float pitch) {
        viewer.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
    }

    private static float number(String[] parts, int at) {
        if (at >= parts.length) {
            return DEFAULT;
        }
        try {
            return Float.parseFloat(parts[at]);
        } catch (NumberFormatException notANumber) {
            return DEFAULT;
        }
    }
}

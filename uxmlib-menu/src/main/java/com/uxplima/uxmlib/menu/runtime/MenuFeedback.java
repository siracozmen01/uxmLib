package com.uxplima.uxmlib.menu.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.gui.style.MenuSounds;
import com.uxplima.uxmlib.menu.spec.Ref;

/**
 * The audible half of a menu click. A button that reacts silently reads as a button that did nothing, so the engine
 * gives every gesture it accepts a short sound: a click for an ordinary action, a page turn for pagination, and a low
 * note for a refusal.
 *
 * <p>Which sounds those are is not the engine's decision. The tones come from the {@link MenuSounds} the host wires in,
 * which reads them from that host's own configuration file, so an operator retunes or silences any of them without
 * touching code. The engine owns when a sound plays; the file owns what it is.
 *
 * <p>The sound is feedback, never content. A gesture the engine ignored (a filler pane, a slot with no bound action)
 * stays silent. A gesture whose own actions already play something should speak for itself rather than be spoken over,
 * but the engine cannot tell which actions those are: an action id belongs to the vocabulary the host registered, and
 * the library must not hold a copy of one consumer's action names. So the host answers that question through {@code
 * speaksForItself}, and the shipped answer is that nothing does. A wrong suppression is inaudible and stays a mystery;
 * a wrong doubling is audible and is fixed in one line, so the default falls on the side that can be heard.
 */
final class MenuFeedback {

    /** The engine wired without a host answer: nothing speaks for itself, so nothing is suppressed. */
    static final Predicate<List<Ref>> SUPPRESSES_NOTHING = actions -> false;

    private final MenuSounds sounds;

    private final Predicate<List<Ref>> speaksForItself;

    MenuFeedback(MenuSounds sounds, Predicate<List<Ref>> speaksForItself) {
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        this.speaksForItself = Objects.requireNonNull(speaksForItself, "speaksForItself");
    }

    /**
     * Play the ordinary click tone for a gesture that ran {@code actions}. An empty list means the engine accepted
     * nothing, and a gesture the host says speaks for itself is left alone; both stay silent.
     */
    void click(MenuHolder holder, List<Ref> actions) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(actions, "actions");
        if (actions.isEmpty() || speaksForItself.test(actions)) {
            return;
        }
        play(holder, sounds.click());
    }

    /**
     * Play the page-turn tone for a pagination button that ran {@code actions}. A page button carries actions like any
     * other, so it is put to the same question: a spec that puts its own sound on a page button gets that sound alone.
     */
    void page(MenuHolder holder, List<Ref> actions) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(actions, "actions");
        if (speaksForItself.test(actions)) {
            return;
        }
        play(holder, sounds.page());
    }

    /**
     * Play the refusal tone for a gesture whose requirements denied it. Nothing ran, so there is nothing that could
     * have spoken for itself, and this one is never suppressed.
     */
    void deny(MenuHolder holder) {
        Objects.requireNonNull(holder, "holder");
        play(holder, sounds.denied());
    }

    /**
     * Play {@code sound} to the viewer at their own position. The viewer is resolved live, so a player who logged off
     * between the click and this call is simply skipped rather than throwing into the click dispatch.
     */
    private static void play(MenuHolder holder, Sound sound) {
        Player viewer = holder.ctx().viewer();
        if (!viewer.isOnline()) {
            return;
        }
        viewer.playSound(sound);
    }
}

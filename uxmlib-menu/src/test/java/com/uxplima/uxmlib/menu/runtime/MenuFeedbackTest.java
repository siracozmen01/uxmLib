package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.function.Predicate;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import com.uxplima.uxmlib.gui.style.MenuSounds;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.spec.Ref;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.sound.AudioExperience;

/**
 * When the engine makes a noise. Which noise is the host's file to decide, so every assertion here is about the
 * decision the engine does own: whether a gesture gets a sound at all.
 *
 * <p>Three sounds that cannot be confused with each other stand in for the host's tones, because the interesting
 * cases are the ones where the wrong sound would still be a sound. A test asserting only that something played would
 * pass with a click tone on a refusal.
 */
class MenuFeedbackTest {

    private static final Key CLICK = Key.key("test", "click");

    private static final Key PAGE = Key.key("test", "page");

    private static final Key DENIED = Key.key("test", "denied");

    private static final List<Ref> SOME_ACTIONS = List.of(Ref.parse("give:diamond"));

    /** The host says every gesture speaks for itself, the opposite of the shipped answer. */
    private static boolean suppressesEverything(List<Ref> actions) {
        return true;
    }

    private PlayerMock viewer;

    private MenuHolder holder;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        holder = new MenuHolder("menu", new MenuSpecLoader().parse("rows = 3"), MenuContext.of(viewer, null, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static MenuSounds sounds() {
        return new MenuSounds(
                Sound.sound(Key.key("test", "open"), Sound.Source.MASTER, 1f, 1f),
                Sound.sound(CLICK, Sound.Source.MASTER, 1f, 1f),
                Sound.sound(PAGE, Sound.Source.MASTER, 1f, 1f),
                Sound.sound(DENIED, Sound.Source.MASTER, 1f, 1f));
    }

    private MenuFeedback feedback(Predicate<List<Ref>> speaksForItself) {
        return new MenuFeedback(sounds(), speaksForItself);
    }

    private List<String> heard() {
        return viewer.getHeardSounds().stream().map(AudioExperience::getSound).toList();
    }

    // -- an ordinary click ---------------------------------------------------------------------------------

    @Test
    void aGestureThatRanSomethingGetsTheClickTone() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).click(holder, SOME_ACTIONS);

        assertThat(heard()).containsExactly(CLICK.asString());
    }

    /** A slot with nothing bound to it did nothing, and a sound would tell the viewer it did something. */
    @Test
    void aGestureThatRanNothingIsSilent() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).click(holder, List.of());

        assertThat(heard()).isEmpty();
    }

    @Test
    void aGestureTheHostSaysSpeaksForItselfIsNotSpokenOver() {
        feedback(MenuFeedbackTest::suppressesEverything).click(holder, SOME_ACTIONS);

        assertThat(heard()).isEmpty();
    }

    // -- a page turn ---------------------------------------------------------------------------------------

    @Test
    void aPageButtonGetsThePageToneAndNotTheClickOne() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).page(holder, SOME_ACTIONS);

        assertThat(heard()).containsExactly(PAGE.asString());
    }

    /**
     * A page button with no actions of its own still turned the page, so unlike an ordinary click it is not silent.
     * The two methods differ on exactly this and nothing else, which is why it is asserted rather than assumed.
     */
    @Test
    void aPageButtonWithNoActionsStillSoundsBecauseThePageStillTurned() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).page(holder, List.of());

        assertThat(heard()).containsExactly(PAGE.asString());
    }

    @Test
    void aPageButtonCarryingItsOwnSoundIsNotSpokenOver() {
        feedback(MenuFeedbackTest::suppressesEverything).page(holder, SOME_ACTIONS);

        assertThat(heard()).isEmpty();
    }

    // -- a refusal -----------------------------------------------------------------------------------------

    @Test
    void aDeniedGestureGetsTheRefusalTone() {
        feedback(MenuFeedback.SUPPRESSES_NOTHING).deny(holder);

        assertThat(heard()).containsExactly(DENIED.asString());
    }

    /**
     * Nothing ran, so nothing could have spoken for itself. A refusal is the one tone the host cannot suppress, and a
     * silent refusal is indistinguishable from a broken button.
     */
    @Test
    void aRefusalSoundsEvenWhenTheHostSuppressesEverythingElse() {
        feedback(MenuFeedbackTest::suppressesEverything).deny(holder);

        assertThat(heard()).containsExactly(DENIED.asString());
    }

    // -- the shipped answer, and a viewer who left -----------------------------------------------------------

    /**
     * The default falls on the side that can be heard. A wrong suppression is inaudible and stays a mystery; a wrong
     * doubling is audible and is fixed in one line.
     */
    @Test
    void theShippedAnswerSuppressesNothing() {
        assertThat(MenuFeedback.SUPPRESSES_NOTHING.test(SOME_ACTIONS)).isFalse();
        assertThat(MenuFeedback.SUPPRESSES_NOTHING.test(List.of())).isFalse();
    }

    /** A player who logged off between the click and the sound is skipped, not thrown into the click dispatch. */
    @Test
    void aViewerWhoLeftBetweenTheClickAndTheSoundIsSkippedQuietly() {
        viewer.disconnect();

        assertThatCode(() -> feedback(MenuFeedback.SUPPRESSES_NOTHING).click(holder, SOME_ACTIONS))
                .doesNotThrowAnyException();
        assertThat(heard()).isEmpty();
    }
}

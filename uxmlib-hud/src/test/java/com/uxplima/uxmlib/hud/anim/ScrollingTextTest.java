package com.uxplima.uxmlib.hud.anim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/**
 * The scrolling ticker is pure: each {@link TextAnimator#advance()} rotates a fixed-width window over the
 * source text by one character, so a known source yields a known frame sequence with no Bukkit in play.
 */
class ScrollingTextTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void windowScrollsOneCharacterPerAdvance() {
        ScrollingText ticker = ScrollingText.of("ABCDE", 3);

        assertThat(plain(ticker.frame())).isEqualTo("ABC");
        ticker.advance();
        assertThat(plain(ticker.frame())).isEqualTo("BCD");
        ticker.advance();
        assertThat(plain(ticker.frame())).isEqualTo("CDE");
    }

    @Test
    void windowWrapsAroundWithASeparatorGap() {
        ScrollingText ticker = ScrollingText.of("AB", 3, " ");
        // Source becomes "AB " conceptually (text + separator) and wraps.
        assertThat(plain(ticker.frame())).isEqualTo("AB ");
        ticker.advance();
        assertThat(plain(ticker.frame())).isEqualTo("B A");
        ticker.advance();
        assertThat(plain(ticker.frame())).isEqualTo(" AB");
        ticker.advance();
        assertThat(plain(ticker.frame())).isEqualTo("AB ");
    }

    @Test
    void shortSourceIsReturnedWholeWithoutScrolling() {
        ScrollingText ticker = ScrollingText.of("HI", 5);
        assertThat(plain(ticker.frame())).isEqualTo("HI");
        ticker.advance();
        assertThat(plain(ticker.frame())).isEqualTo("HI");
    }

    @Test
    void resetReturnsToTheFirstFrame() {
        ScrollingText ticker = ScrollingText.of("ABCDE", 3);
        ticker.advance();
        ticker.advance();
        ticker.reset();
        assertThat(plain(ticker.frame())).isEqualTo("ABC");
    }

    @Test
    void rejectsNonPositiveWidth() {
        assertThatThrownBy(() -> ScrollingText.of("abc", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSource() {
        assertThatThrownBy(() -> ScrollingText.of("", 3)).isInstanceOf(IllegalArgumentException.class);
    }
}

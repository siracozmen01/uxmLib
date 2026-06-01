package com.uxplima.uxmlib.hologram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

/** Pure tests of the animation frame builders — no server, no entity. */
class TextAnimationTest {

    @Test
    void typewriterRevealsOneCharacterPerFrame() {
        List<Component> frames = TextAnimation.typewriter("abc");
        assertThat(frames).hasSize(3);
        assertThat(Text.plain(frames.get(0))).isEqualTo("a");
        assertThat(Text.plain(frames.get(1))).isEqualTo("ab");
        assertThat(Text.plain(frames.get(2))).isEqualTo("abc");
    }

    @Test
    void scrollSlidesAWindowAndLoops() {
        List<Component> frames = TextAnimation.scroll("hi", 2);
        // "hi" + 2 spaces padding = 4 chars, so 4 frames each 2 wide.
        assertThat(frames).hasSize(4);
        assertThat(Text.plain(frames.get(0))).isEqualTo("hi");
        // The window wraps around the padded string so it loops cleanly.
        assertThat(Text.plain(frames.get(frames.size() - 1))).hasSize(2);
    }

    @Test
    void framesCopiesAndRejectsEmpty() {
        List<Component> frames = TextAnimation.frames(List.of(Component.text("x")));
        assertThat(frames).hasSize(1);
        assertThatThrownBy(() -> TextAnimation.frames(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scrollRejectsZeroWindow() {
        assertThatThrownBy(() -> TextAnimation.scroll("x", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

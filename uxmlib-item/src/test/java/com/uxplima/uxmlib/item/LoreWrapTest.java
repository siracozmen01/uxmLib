package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class LoreWrapTest {

    @Test
    void keepsAShortLineWhole() {
        assertThat(LoreWrap.wrap("a short line", 40)).containsExactly("a short line");
    }

    @Test
    void wrapsAtWordBoundariesUnderTheWidth() {
        List<String> lines = LoreWrap.wrap("the quick brown fox jumps over", 12);

        assertThat(lines).containsExactly("the quick", "brown fox", "jumps over");
        assertThat(lines).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(12));
    }

    @Test
    void splitsOnExplicitNewlinesBeforeWrapping() {
        assertThat(LoreWrap.wrap("first line\nsecond line", 40)).containsExactly("first line", "second line");
    }

    @Test
    void measuresWidthAgainstVisibleTextNotMiniMessageTags() {
        // Five visible characters ("hello") under a width of 8, despite the long colour tag.
        assertThat(LoreWrap.wrap("<gradient:red:blue>hello</gradient>", 8))
                .containsExactly("<gradient:red:blue>hello</gradient>");
    }

    @Test
    void keepsAnOverlongUnbreakableWordOnItsOwnLine() {
        // A single token longer than the width can't be split, so it stands alone.
        assertThat(LoreWrap.wrap("tiny supercalifragilistic end", 10))
                .containsExactly("tiny", "supercalifragilistic", "end");
    }

    @Test
    void rejectsANonPositiveWidth() {
        assertThatThrownBy(() -> LoreWrap.wrap("x", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

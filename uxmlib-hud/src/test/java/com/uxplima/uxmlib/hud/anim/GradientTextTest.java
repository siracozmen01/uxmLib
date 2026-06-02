package com.uxplima.uxmlib.hud.anim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

/**
 * The animated gradient is pure: every character of the source carries its own interpolated colour and the
 * whole sweep shifts by one phase step each {@link TextAnimator#advance()}, so a fixed source yields a fixed,
 * assertable colour sequence with no Bukkit in play. This is the thing MiniMessage cannot do statically.
 */
class GradientTextTest {

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Flatten the per-character children into their colours, top to bottom. */
    private static List<TextColor> colours(Component frame) {
        return frame.children().stream()
                .map(child -> java.util.Objects.requireNonNull(((TextComponent) child).color()))
                .toList();
    }

    @Test
    void framePreservesTheSourceText() {
        GradientText gradient = GradientText.of("HELLO", List.of(NamedTextColor.RED, NamedTextColor.BLUE));
        assertThat(plain(gradient.frame())).isEqualTo("HELLO");
    }

    @Test
    void everyCharacterIsItsOwnColouredChild() {
        GradientText gradient = GradientText.of("ABC", List.of(NamedTextColor.RED, NamedTextColor.BLUE));
        List<TextColor> colours = colours(gradient.frame());
        assertThat(colours).hasSize(3);
        // Endpoints sit at the gradient extremes on the first frame.
        assertThat(colours.get(0)).isEqualTo(TextColor.color(NamedTextColor.RED));
    }

    @Test
    void advancingShiftsTheColourSweep() {
        GradientText gradient = GradientText.of("ABCD", List.of(NamedTextColor.RED, NamedTextColor.BLUE));
        List<TextColor> before = colours(gradient.frame());
        gradient.advance();
        List<TextColor> after = colours(gradient.frame());
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    void theSweepIsPeriodicOverTheStepCount() {
        GradientText gradient = GradientText.of("ABCD", List.of(NamedTextColor.RED, NamedTextColor.BLUE), 8);
        List<TextColor> start = colours(gradient.frame());
        for (int i = 0; i < 8; i++) {
            gradient.advance();
        }
        assertThat(colours(gradient.frame())).isEqualTo(start);
    }

    @Test
    void resetReturnsToTheFirstFrame() {
        GradientText gradient = GradientText.of("ABCD", List.of(NamedTextColor.RED, NamedTextColor.BLUE));
        List<TextColor> start = colours(gradient.frame());
        gradient.advance();
        gradient.advance();
        gradient.reset();
        assertThat(colours(gradient.frame())).isEqualTo(start);
    }

    @Test
    void rejectsFewerThanTwoStops() {
        assertThatThrownBy(() -> GradientText.of("abc", List.of(NamedTextColor.RED)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSource() {
        assertThatThrownBy(() -> GradientText.of("", List.of(NamedTextColor.RED, NamedTextColor.BLUE)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

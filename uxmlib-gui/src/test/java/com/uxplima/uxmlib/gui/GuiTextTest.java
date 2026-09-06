package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** What the defaults on the text seam do, including the one case that fails narrowly. */
class GuiTextTest {

    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    /** The key becomes the text, so a test can see which of the two questions was asked. */
    private static final GuiText ECHO = new GuiText() {
        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            return Component.text(key + placeholders);
        }

        @Override
        public Component render(String raw) {
            return Component.text("rendered:" + raw);
        }
    };

    @Test
    void theKeyOnlyOverloadAsksForNoPlaceholders() {
        assertThat(plain(ECHO.text(player(), "menu.title"))).isEqualTo("menu.title{}");
    }

    @Test
    void plainFlattensWhatTextReturned() {
        assertThat(ECHO.plain(player(), "menu.title", Map.of())).isEqualTo("menu.title{}");
    }

    /**
     * The narrow failure the javadoc warns about. Adventure flattens a translatable component that no
     * translator holds to the <strong>empty string</strong>, not to its key, so a Bedrock form label would go
     * blank while the same text renders normally in a lore line. Both readings of that were wrong before it
     * was run: the guess was that the key survives, so the flattening itself is pinned below.
     *
     * <p>{@code plain} therefore hands back the key, which is what the client does with a translation it does
     * not have. A player sees {@code menu.confirm.yes} on the button, which is ugly and is the point: it names
     * itself, so whoever wrote the menu can find it.
     */
    @Test
    void aTranslatableWithNoTranslatorDegradesToItsKeyRatherThanVanishing() {
        GuiText translatable = new GuiText() {
            @Override
            public Component text(Player viewer, String key, Map<String, String> placeholders) {
                return Component.translatable(key);
            }

            @Override
            public Component render(String raw) {
                return Component.text(raw);
            }
        };

        assertThat(PlainTextComponentSerializer.plainText()
                        .serialize(translatable.text(player(), "uxmlib.test.absent.key", Map.of())))
                .as("Adventure loses the whole component, which is the failure being guarded")
                .isEmpty();
        assertThat(translatable.plain(player(), "uxmlib.test.absent.key", Map.of()))
                .as("so plain names the key instead of handing back a blank label")
                .isEqualTo("uxmlib.test.absent.key");
    }

    /** Text an implementation meant to be empty is not a loss, so it is not replaced by the key. */
    @Test
    void anEmptyStringIsNotReportedAsALoss() {
        GuiText empty = new GuiText() {
            @Override
            public Component text(Player viewer, String key, Map<String, String> placeholders) {
                return Component.text("");
            }

            @Override
            public Component render(String raw) {
                return Component.text(raw);
            }
        };

        assertThat(empty.plain(player(), "menu.blank", Map.of())).isEmpty();
    }

    /**
     * A translatable nested under an empty text parent still loses everything, so the walk looks down rather than
     * testing the top component alone. Note the parent is empty: a parent with words in it is partial loss, which
     * this does not detect and does not claim to.
     */
    @Test
    void aNestedTranslatableIsFoundToo() {
        GuiText nested = new GuiText() {
            @Override
            public Component text(Player viewer, String key, Map<String, String> placeholders) {
                return Component.text("").append(Component.translatable(key));
            }

            @Override
            public Component render(String raw) {
                return Component.text(raw);
            }
        };

        assertThat(nested.plain(player(), "uxmlib.test.nested.key", Map.of())).isEqualTo("uxmlib.test.nested.key");
    }

    /**
     * Partial loss is not detected, and that is pinned rather than left to the javadoc. Text beside a translatable
     * survives the flatten, so the result is not empty and no key is substituted: half the line is simply gone. It
     * is left alone because splicing a key into the surviving words would read worse than either half, but a
     * reader who sees the walk in {@code plain} must not conclude that translatables are handled.
     */
    @Test
    void wordsBesideATranslatableSurviveAndTheTranslatableHalfIsLostSilently() {
        GuiText partial = new GuiText() {
            @Override
            public Component text(Player viewer, String key, Map<String, String> placeholders) {
                return Component.text("Yes: ").append(Component.translatable(key));
            }

            @Override
            public Component render(String raw) {
                return Component.text(raw);
            }
        };

        assertThat(partial.plain(player(), "uxmlib.test.partial.key", Map.of()))
                .as("the surviving words come back and the key does not, which is the loss being documented")
                .isEqualTo("Yes: ");
    }

    /** A catalog with no chat decoration answers both questions the same way, and needs to override nothing. */
    @Test
    void unprefixedFallsBackToTheSameWordsWhenAConsumerHasNoPrefix() {
        assertThat(plain(ECHO.textUnprefixed(player(), "menu.title", Map.of()))).isEqualTo("menu.title{}");
    }

    /** A consumer that does have decoration overrides it, and the two questions then differ. */
    @Test
    void unprefixedIsTheOverridePointForAConsumerThatHasOne() {
        GuiText branded = new GuiText() {
            @Override
            public Component text(Player viewer, String key, Map<String, String> placeholders) {
                return Component.text("brand " + key);
            }

            @Override
            public Component render(String raw) {
                return Component.text(raw);
            }

            @Override
            public Component textUnprefixed(Player viewer, String key, Map<String, String> placeholders) {
                return Component.text(key);
            }
        };

        assertThat(plain(branded.text(player(), "menu.title"))).isEqualTo("brand menu.title");
        assertThat(plain(branded.textUnprefixed(player(), "menu.title", Map.of())))
                .isEqualTo("menu.title");
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }

    private static Player player() {
        return MockBukkit.getMock().addPlayer();
    }
}

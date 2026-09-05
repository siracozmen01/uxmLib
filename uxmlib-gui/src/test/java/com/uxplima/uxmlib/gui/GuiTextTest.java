package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

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
     * The narrow failure the javadoc warns about, and it is worse than losing the words. A translatable
     * component that no translator holds flattens to the <strong>empty string</strong>, not to its key. So a
     * Bedrock form label goes blank while the same text renders normally in a lore line, which keeps the
     * component and lets the client translate it. A blank label tells the player nothing and tells whoever
     * wrote the menu nothing either.
     *
     * <p>Pinned here because both readings of this were wrong before it was run: the guess was that the key
     * survives.
     */
    @Test
    void aTranslatableComponentFlattensToNothingWhenNoTranslatorHoldsIt() {
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

        assertThat(translatable.plain(player(), "uxmlib.test.absent.key", Map.of()))
                .isEmpty();
    }

    /** Text that is legitimately empty is not a loss, so the guard must not report it. */
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

    /** A translatable nested under a text parent loses everything just the same, so the guard looks down. */
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

        assertThat(nested.plain(player(), "uxmlib.test.nested.key", Map.of())).isEmpty();
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }

    private static Player player() {
        return MockBukkit.getMock().addPlayer();
    }
}

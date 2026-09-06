package com.uxplima.uxmlib.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The dialog backend, seen through its show seam. A native dialog cannot be driven under MockBukkit, so what is
 * checked here is everything the backend decides before the screen exists: which words the two buttons carry, what
 * the field is seeded with, and what a submit and a dismissal are reported as.
 *
 * <p>The buttons are the point. The screen refuses to be built without them, and this library ships no words, so
 * they have to come from the caller's catalog for the viewer who is looking at the prompt. A backend that invented
 * them would work on our own server and give every other consumer two English words they never wrote.
 */
class DialogTextBackendTest {

    /** Answers a key with the key itself, so a test can tell which key a word was asked for under. */
    private static final class KeyEcho implements GuiText {

        private final List<String> asked = new ArrayList<>();

        @Override
        public Component text(Player viewer, String key, Map<String, String> placeholders) {
            asked.add(key);
            return Component.text("<" + key + ">");
        }

        @Override
        public Component render(String raw) {
            return Component.text(raw);
        }
    }

    /** Records the one show it was given, and keeps the callbacks so a test can drive them. */
    private static final class Recording implements DialogTextBackend.Prompt {

        private @Nullable Component title;
        private @Nullable Component label;
        private @Nullable Component submitLabel;
        private @Nullable Component cancelLabel;
        private @Nullable String initial;
        private @Nullable Consumer<String> onSubmit;
        private @Nullable Runnable onCancel;

        @Override
        public void show(
                Player player,
                Component title,
                Component label,
                Component submitLabel,
                Component cancelLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            this.title = title;
            this.label = label;
            this.submitLabel = submitLabel;
            this.cancelLabel = cancelLabel;
            this.initial = initial;
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }
    }

    private static String plain(@Nullable Component component) {
        return PlainTextComponentSerializer.plainText().serialize(java.util.Objects.requireNonNull(component));
    }

    private final KeyEcho words = new KeyEcho();
    private final Recording prompt = new Recording();
    private final List<InputResult> results = new ArrayList<>();

    private Player viewer;

    private DialogTextBackend backend;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        backend = new DialogTextBackend(prompt, words);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theTwoButtonWordsComeFromTheCallersCatalog() {
        backend.open(viewer, Component.text("a name"), null, results::add);

        assertThat(plain(prompt.submitLabel)).isEqualTo("<gui.input.submit>");
        assertThat(plain(prompt.cancelLabel)).isEqualTo("<gui.input.cancel>");
        assertThat(words.asked).containsExactly(TextInput.SUBMIT_KEY, TextInput.CANCEL_KEY);
    }

    /** The prompt is one line, so the resolved text is both the window title and the label beside the field. */
    @Test
    void thePromptItselfIsNotAskedOfTheCatalogASecondTime() {
        backend.open(viewer, Component.text("a name"), null, results::add);

        assertThat(plain(prompt.title)).isEqualTo("a name");
        assertThat(plain(prompt.label)).isEqualTo("a name");
        assertThat(words.asked).doesNotContain("a name");
    }

    /** A dialog field can be seeded, which is the one thing this backend does that the sign backend cannot. */
    @Test
    void theFieldIsSeededWithTheTextTheCallerPassed() {
        backend.open(viewer, Component.text("a name"), "spawn", results::add);

        assertThat(prompt.initial).isEqualTo("spawn");
    }

    /** Nothing to seed is nothing to seed: the field opens empty rather than with a word of the library's. */
    @Test
    void aPromptWithNothingToSeedSeedsNothing() {
        backend.open(viewer, Component.text("a name"), null, results::add);

        assertThat(prompt.initial).isNull();
    }

    @Test
    void aTypedLineIsReportedAsASubmission() {
        backend.open(viewer, Component.text("a name"), null, results::add);

        java.util.Objects.requireNonNull(prompt.onSubmit).accept("spawn");

        assertThat(results).containsExactly(new InputResult.Submitted("spawn"));
    }

    @Test
    void aDismissedDialogIsReportedAsACancellation() {
        backend.open(viewer, Component.text("a name"), null, results::add);

        java.util.Objects.requireNonNull(prompt.onCancel).run();

        assertThat(results).containsExactly(InputResult.Cancelled.INSTANCE);
    }
}

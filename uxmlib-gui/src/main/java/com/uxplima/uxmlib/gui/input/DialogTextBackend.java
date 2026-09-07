package com.uxplima.uxmlib.gui.input;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.gui.dialog.DialogInputScreen;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The dialog backend of the text-input seam: prompts through uxmLib's native {@link DialogInputScreen} (the Paper
 * server-side Dialog carrying one line of text, added in Minecraft 1.21.6) and reports the typed line. A dialog field
 * can be pre-seeded, so unlike the sign backend this honours {@code initialText}. The uxmLib screen delivers its submit
 * and cancel on the main server thread; the entity-thread hop and the cancel-keyword check live upstream in {@link
 * TextInput} (its outcome wrapper marshals every backend's result onto the player's region), so this stays a thin
 * adapter exactly like {@link SignTextBackend}: it never schedules for itself.
 *
 * <p>A live {@link DialogInputScreen} cannot be driven under MockBukkit, so the show step is the {@link Prompt} seam:
 * production supplies the native, screen-backed one via {@link #paperNative(GuiText)}; a test supplies a fake that
 * drives the submit and cancel callbacks directly, so the submit/cancel contract is covered without a live Paper
 * dialog.
 *
 * <p>The two button words are the only text this backend needs that the prompt does not carry. They are asked of the
 * caller's catalog, per viewer, under {@link TextInput#SUBMIT_KEY} and {@link TextInput#CANCEL_KEY}: the screen
 * demands them, and this library ships no words of its own. Those two keys were renamed from the dialog-specific
 * {@link TextInput#LEGACY_SUBMIT_KEY} pair, so a catalog with nothing under the current name is asked the older one
 * rather than being left to print a raw key onto a button; see {@link #buttonWord}.
 */
@NullMarked
final class DialogTextBackend implements TextInputBackend {

    /** The dialog's internal field key the typed line is read back by; never shown to the player. */
    private static final String FIELD_KEY = "input";

    /**
     * Shows a one-line text dialog to a player and delivers the typed line to {@code onSubmit}, or a dismissal to
     * {@code onCancel}. Production is the native {@link DialogInputScreen}; a test fakes it so the backend's contract is
     * exercised without a live Paper dialog.
     */
    @FunctionalInterface
    interface Prompt {
        void show(
                Player player,
                Component title,
                Component label,
                Component submitLabel,
                Component cancelLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onCancel);
    }

    private final Prompt prompt;
    private final GuiText guiText;

    DialogTextBackend(Prompt prompt, GuiText guiText) {
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
    }

    /** The production backend, prompting through a native {@link DialogInputScreen}. */
    static DialogTextBackend paperNative(GuiText guiText) {
        return new DialogTextBackend(DialogTextBackend::showNative, guiText);
    }

    @Override
    public void open(Player player, Component promptText, @Nullable String initialText, Consumer<InputResult> outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(promptText, "prompt");
        Objects.requireNonNull(outcome, "outcome");
        // The screen carries a single line, so the resolved prompt serves as both the window title and the field
        // label. TextInput's outcome wrapper owns the hop onto the player's region, so this forwards raw.
        prompt.show(
                player,
                promptText,
                promptText,
                buttonWord(player, TextInput.SUBMIT_KEY, TextInput.LEGACY_SUBMIT_KEY),
                buttonWord(player, TextInput.CANCEL_KEY, TextInput.LEGACY_CANCEL_KEY),
                initialText,
                line -> outcome.accept(new InputResult.Submitted(line)),
                () -> outcome.accept(InputResult.Cancelled.INSTANCE));
    }

    /**
     * One button word for {@code player}: the catalog entry under {@code key}, or the one under {@code legacyKey}
     * when the catalog holds nothing under the current name.
     *
     * <p>The pair was renamed from the dialog-specific {@code gui.input.dialog-*} to the generic {@code gui.input.*}
     * with no way back, so a catalog written against the older pair put the literal text {@code gui.input.submit} on
     * the button. The current name is asked first, because it is the one this library uses and the one a new catalog
     * is written against; a consumer that has already moved therefore pays one lookup and never sees the older name.
     *
     * <p>A catalog holding neither answers the current key, which is the convention {@link GuiText#plain} already
     * states: a key on the screen puts the failure in front of somebody, in words, naming the entry to add. That is
     * why the current word rather than the older one is what comes back when both are missing.
     */
    private Component buttonWord(Player player, String key, String legacyKey) {
        Component current = guiText.text(player, key, Map.of());
        if (!unwritten(current, key)) {
            return current;
        }
        Component older = guiText.text(player, legacyKey, Map.of());
        return unwritten(older, legacyKey) ? current : older;
    }

    /**
     * Whether {@code words} is what a catalog answers for a key nobody wrote: the key itself, or nothing at all. A
     * catalog is the consumer's file and this interface has no "do you hold this key" question, so the answer has to
     * be read off the words. Both forms are what implementations actually do, and {@link GuiText#plain} documents the
     * first as the library's own convention.
     */
    private static boolean unwritten(Component words, String key) {
        String flattened = PlainTextComponentSerializer.plainText().serialize(words);
        return flattened.isBlank() || flattened.equals(key);
    }

    private static void showNative(
            Player player,
            Component title,
            Component label,
            Component submitLabel,
            Component cancelLabel,
            @Nullable String initial,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        DialogInputScreen screen = DialogInputScreen.create(title, FIELD_KEY, label, submitLabel, cancelLabel);
        if (initial != null) {
            screen.initial(initial);
        }
        screen.prompt(player, onSubmit, onCancel);
    }
}

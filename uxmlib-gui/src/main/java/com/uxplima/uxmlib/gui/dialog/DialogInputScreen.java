package com.uxplima.uxmlib.gui.dialog;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

import com.uxplima.uxmlib.common.ServerVersion;

/**
 * A fluent facade for a Paper server-side {@code Dialog} that carries a single line of text input (added in
 * Minecraft 1.21.6). It is the sibling of {@link DialogScreen}: where that shows a notice or a yes / no
 * confirmation, this prompts the player to type one value (a warp name, a description, a password), and
 * hands the typed line to a callback, so a consumer can ask for text through the native dialog instead of an
 * anvil or a sign. Create one with {@link #create}, tune the field with the fluent setters, then
 * {@link #prompt(Player, Consumer, Runnable)} it.
 *
 * <p>The dialog shows the submit and cancel buttons of a confirmation: pressing submit reads the field and
 * runs {@code onSubmit} with the typed line; pressing cancel runs {@code onCancel}. Closing the dialog with
 * the escape key dismisses it without running either callback.
 *
 * <p><strong>Threading.</strong> Paper delivers the dialog action on the main server thread: the same
 * thread every other dialog button callback runs on, so {@code onSubmit} and {@code onCancel} run there.
 * This facade never schedules; the consumer decides threading. Do not block the callback; hop to async
 * yourself for any I/O the typed value drives.
 *
 * <p>The feature is version-gated. On a server older than 1.21.6 the Dialog API does not exist, so
 * {@link #isSupported()} returns {@code false} and {@link #prompt(Player, Consumer, Runnable)} runs
 * {@code onCancel} instead of touching the absent API (a consumer on an older server degrades cleanly rather
 * than hitting a {@code NoClassDefFoundError}); {@link #build(Consumer, Runnable)} on such a server throws,
 * since there is nothing to build.
 *
 * <p>Both button labels are given to {@link #create} and neither is shipped. A label is a word a player reads,
 * so it belongs in the message file the consumer translates, not in this jar. The field's size and its
 * pre-filled value do have defaults, because a character cap and a pixel width are geometry rather than
 * words: nobody has to translate 128.
 */
public final class DialogInputScreen {

    private static final int DEFAULT_WIDTH = 200;
    private static final int MAX_WIDTH = 1024;
    private static final int DEFAULT_MAX_LENGTH = 128;

    private final Component title;
    private final String key;
    private final Component label;
    private final Component submitLabel;
    private final Component cancelLabel;
    private String initial = "";
    private int maxLength = DEFAULT_MAX_LENGTH;
    private int width = DEFAULT_WIDTH;

    private DialogInputScreen(
            Component title, String key, Component label, Component submitLabel, Component cancelLabel) {
        this.title = Objects.requireNonNull(title, "title");
        this.key = requireKey(key);
        this.label = Objects.requireNonNull(label, "label");
        this.submitLabel = Objects.requireNonNull(submitLabel, "submitLabel");
        this.cancelLabel = Objects.requireNonNull(cancelLabel, "cancelLabel");
    }

    /**
     * A text-input dialog with the given window {@code title}, the input {@code key} the typed line is read
     * back by, the {@code label} shown beside the field, and the words on its two buttons.
     *
     * @param submitLabel the word on the button that delivers the typed line, from your own message file
     * @param cancelLabel the word on the button that abandons it, from your own message file
     * @throws IllegalArgumentException if {@code key} is blank
     */
    public static DialogInputScreen create(
            Component title, String key, Component label, Component submitLabel, Component cancelLabel) {
        return new DialogInputScreen(title, key, label, submitLabel, cancelLabel);
    }

    /** Whether the running server supports the Dialog API (Minecraft 1.21.6 or newer). */
    public static boolean isSupported() {
        return ServerVersion.current().isAtLeast(1, 21, 6);
    }

    /** Pre-fill the field with {@code initial} (empty by default). */
    public DialogInputScreen initial(String initial) {
        this.initial = Objects.requireNonNull(initial, "initial");
        return this;
    }

    /** Cap the field at {@code maxLength} characters (128 by default). */
    public DialogInputScreen maxLength(int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be >= 1, was " + maxLength);
        }
        this.maxLength = maxLength;
        return this;
    }

    /** Set the field's on-screen width in pixels, {@code 1..1024} (200 by default). */
    public DialogInputScreen width(int width) {
        if (width < 1 || width > MAX_WIDTH) {
            throw new IllegalArgumentException("width must be within 1.." + MAX_WIDTH + ", was " + width);
        }
        this.width = width;
        return this;
    }

    /**
     * Show the dialog to {@code player}: on submit the typed line is handed to {@code onSubmit}, on cancel
     * {@code onCancel} runs. On a server too old for the Dialog API this runs {@code onCancel} rather than
     * touching the absent API, so callers do not need to guard every call site with {@link #isSupported()}.
     */
    public void prompt(Player player, Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        deliver(player, onSubmit, onCancel, isSupported());
    }

    /**
     * Build the native {@link Dialog} with the submit and cancel callbacks. Only valid on a 1.21.6+ server;
     * throws {@link IllegalStateException} otherwise (guard with {@link #isSupported()} or call
     * {@link #prompt(Player, Consumer, Runnable)}, which degrades instead).
     */
    public Dialog build(Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        if (!isSupported()) {
            throw new IllegalStateException("server-side dialogs need Minecraft 1.21.6 or newer");
        }
        ActionButton submit = actionButton(submitLabel, submitCallback(onSubmit));
        ActionButton cancel = actionButton(cancelLabel, (response, audience) -> onCancel.run());
        DialogBase base = DialogBase.builder(title).inputs(List.of(toInput())).build();
        return Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(submit, cancel)));
    }

    // Separated from prompt so a test can drive the unsupported branch without a matching host version.
    void deliver(Player player, Consumer<String> onSubmit, Runnable onCancel, boolean supported) {
        if (!supported) {
            onCancel.run();
            return;
        }
        player.showDialog(build(onSubmit, onCancel));
    }

    /** The text input this facade carries into the dialog, mapped from the fluent state. */
    TextDialogInput toInput() {
        return DialogInput.text(key, label)
                .initial(initial)
                .maxLength(maxLength)
                .width(width)
                .build();
    }

    /** The submit callback: read the field by key and deliver it to {@code onSubmit}. */
    DialogActionCallback submitCallback(Consumer<String> onSubmit) {
        Objects.requireNonNull(onSubmit, "onSubmit");
        return (response, audience) -> onSubmit.accept(currentText(response));
    }

    /** The field's current value, or empty when the response carries nothing under our key. */
    String currentText(DialogResponseView response) {
        Objects.requireNonNull(response, "response");
        String value = response.getText(key);
        return value == null ? "" : value;
    }

    private ActionButton actionButton(Component buttonLabel, DialogActionCallback callback) {
        DialogAction action = DialogAction.customClick(
                callback, ClickCallback.Options.builder().build());
        return ActionButton.builder(buttonLabel).action(action).build();
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return key;
    }

    /** The dialog title. */
    Component title() {
        return title;
    }

    /** The input key the typed line is read back by. */
    String key() {
        return key;
    }

    /** The label shown beside the field. */
    Component label() {
        return label;
    }

    /** The field's pre-filled value. */
    String initialValue() {
        return initial;
    }

    /** The field's character cap. */
    int maxLengthValue() {
        return maxLength;
    }

    /** The field's on-screen width in pixels. */
    int widthValue() {
        return width;
    }

    /** The submit button label. */
    Component submitLabelValue() {
        return submitLabel;
    }

    /** The cancel button label. */
    Component cancelLabelValue() {
        return cancelLabel;
    }
}

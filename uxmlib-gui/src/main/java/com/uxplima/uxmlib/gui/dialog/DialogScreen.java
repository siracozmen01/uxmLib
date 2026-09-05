package com.uxplima.uxmlib.gui.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

import com.uxplima.uxmlib.common.ServerVersion;

/**
 * A fluent facade for Paper's server-side {@code Dialog} screens (added in Minecraft 1.21.6): a notice (a
 * single acknowledge button) or a confirmation (yes / no), each with a title, optional body lines, and
 * buttons whose click runs a server-side callback. Build one with {@link #notice} or {@link #confirmation},
 * add body text and buttons, then {@link #show(Player)} it.
 *
 * <p>The feature is version-gated. On a server older than 1.21.6 the Dialog API does not exist, so
 * {@link #isSupported()} returns {@code false} and {@link #show(Player)} is a no-op (it never throws);
 * {@link #build()} on such a server throws, since there is nothing to build.
 *
 * <p>Every button label is given at construction and none is shipped. A label is a word a player reads, so it
 * belongs in the message file the consumer translates, not in this jar: a library that wrote "Yes" would put
 * one English word on a screen no translator can reach.
 */
public final class DialogScreen {

    private final Component title;
    private final boolean confirmation;
    private final List<Component> body = new ArrayList<>();
    private Button primary;
    private Button secondary;

    private DialogScreen(Component title, boolean confirmation, Component primaryLabel, Component secondaryLabel) {
        this.title = Objects.requireNonNull(title, "title");
        this.confirmation = confirmation;
        this.primary = new Button(primaryLabel, audience -> {});
        this.secondary = new Button(secondaryLabel, audience -> {});
    }

    /**
     * A notice dialog: a title, optional body, and a single acknowledge button worded by the caller.
     *
     * @param acknowledgeLabel the word on the one button, from your own message file
     */
    public static DialogScreen notice(Component title, Component acknowledgeLabel) {
        Objects.requireNonNull(acknowledgeLabel, "acknowledgeLabel");
        return new DialogScreen(title, false, acknowledgeLabel, acknowledgeLabel);
    }

    /**
     * A confirmation dialog: a title, optional body, and two buttons worded by the caller.
     *
     * @param yesLabel the word on the accepting button, from your own message file
     * @param noLabel the word on the refusing button, from your own message file
     */
    public static DialogScreen confirmation(Component title, Component yesLabel, Component noLabel) {
        Objects.requireNonNull(yesLabel, "yesLabel");
        Objects.requireNonNull(noLabel, "noLabel");
        return new DialogScreen(title, true, yesLabel, noLabel);
    }

    /** Whether the running server supports the Dialog API (Minecraft 1.21.6 or newer). */
    public static boolean isSupported() {
        return ServerVersion.current().isAtLeast(1, 21, 6);
    }

    /** Add one line of body text. Call repeatedly for several lines. */
    public DialogScreen body(Component line) {
        body.add(Objects.requireNonNull(line, "line"));
        return this;
    }

    /** Set the notice's acknowledge button (a no-op label/handler on a confirmation; use {@link #yes}). */
    public DialogScreen button(Component label, Consumer<Audience> onClick) {
        this.primary = new Button(label, onClick);
        return this;
    }

    /** Set the confirmation's yes button and the handler run when it is pressed. */
    public DialogScreen yes(Component label, Consumer<Audience> onYes) {
        this.primary = new Button(label, onYes);
        return this;
    }

    /** Set the confirmation's no button and the handler run when it is pressed. */
    public DialogScreen no(Component label, Consumer<Audience> onNo) {
        this.secondary = new Button(label, onNo);
        return this;
    }

    /**
     * Build the native {@link Dialog}. Only valid on a 1.21.6+ server; throws {@link IllegalStateException}
     * otherwise (guard with {@link #isSupported()} or just call {@link #show(Player)}, which no-ops instead).
     */
    public Dialog build() {
        if (!isSupported()) {
            throw new IllegalStateException("server-side dialogs need Minecraft 1.21.6 or newer");
        }
        DialogType type = confirmation
                ? DialogType.confirmation(primary.toActionButton(), secondary.toActionButton())
                : DialogType.notice(primary.toActionButton());
        return Dialog.create(factory -> factory.empty().base(base()).type(type));
    }

    /**
     * Show the dialog to {@code player}. A no-op (not an error) on a server too old for the Dialog API, so
     * callers do not need to guard every call site with {@link #isSupported()}.
     */
    public void show(Player player) {
        Objects.requireNonNull(player, "player");
        if (isSupported()) {
            player.showDialog(build());
        }
    }

    private DialogBase base() {
        List<DialogBody> lines = new ArrayList<>(body.size());
        for (Component line : body) {
            lines.add(DialogBody.plainMessage(line));
        }
        return DialogBase.builder(title).body(lines).build();
    }

    /** The dialog title. */
    Component title() {
        return title;
    }

    /** Whether this is a confirmation (yes / no) rather than a notice. */
    boolean isConfirmation() {
        return confirmation;
    }

    /** The body lines added so far, in order. */
    List<Component> bodyLines() {
        return List.copyOf(body);
    }

    /** The notice acknowledge / confirmation yes button label. */
    Component primaryLabel() {
        return primary.label();
    }

    /** The confirmation no button label (unused by a notice). */
    Component secondaryLabel() {
        return secondary.label();
    }

    /** Run the primary button's handler directly, so a test can prove the handler is retained as given. */
    void invokePrimaryForTest(Audience audience) {
        primary.onClick().accept(Objects.requireNonNull(audience, "audience"));
    }

    /** A button's label plus the server-side handler run when it is pressed, before it becomes native. */
    private record Button(Component label, Consumer<Audience> onClick) {
        private Button {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(onClick, "onClick");
        }

        private ActionButton toActionButton() {
            DialogActionCallback callback = (response, audience) -> onClick.accept(audience);
            DialogAction action = DialogAction.customClick(
                    callback, ClickCallback.Options.builder().build());
            return ActionButton.builder(label).action(action).build();
        }
    }
}

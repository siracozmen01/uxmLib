package com.uxplima.uxmlib.gui.input;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.gui.GuiText;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one entry point for capturing a line of text from a player, whether through an anvil or through chat. A call
 * site hands a {@link InputRequest} (a stable key, a prompt label, an optional anvil pre-fill) plus a submit and a
 * cancel callback; the seam reads the operator's per-key mode from {@link InputSettings}, opens the matching backend,
 * and routes the result. It is the upper floor of this package: {@link PlayerInput} and {@link AnvilInput} own the
 * mechanisms that talk to a client, and this chooses between them, applies the operator's policy, and hands a call
 * site one answer. A call site names neither a backend nor a screen.
 *
 * <p><b>Cancel policy, on this floor and no other.</b> Every backend reports a raw {@link InputResult} and the
 * cancel-keyword check lives here. The sign backend runs on {@link PlayerInput}, which can apply a keyword of its
 * own, so it is built with {@link PlayerInput#withoutCancelKeyword} and reports every typed line as a submission.
 * That is what lets the operator's list be the only list: a keyword applied one floor down would arrive already
 * {@code Cancelled}, and a word the operator removed would still abort a sign prompt with nothing to say why.
 * A structural cancel (anvil closed) and a {@code Submitted} line that matches a configured cancel keyword both
 * resolve to a cancellation: the player is sent the {@code gui.input.cancelled} acknowledgement and {@code onCancel}
 * runs (reopening the prior menu, as before). Any other line runs {@code onSubmit} with the typed text.
 *
 * <p><b>Folia.</b> The backend may report on an async thread (chat) or the region thread (anvil); the seam hops both
 * the submit and the cancel branch onto the player's entity region before the callback runs, so a call site's callback
 * always executes where it can safely touch the player and reopen a GUI. The call site no longer hops for itself.
 *
 * <p><b>Bedrock.</b> A Floodgate player has no anvil or chat prompt worth showing, so when {@link BedrockDetector}
 * reports the player is a Bedrock player the seam sends a native Cumulus CustomForm with a single text input instead.
 * Its submitted value and its close both flow through the same {@link #route} policy as the anvil/chat backends, so the
 * cancel-keyword check and the entity-thread hop live in one place regardless of which prompt the player saw. A Java
 * player keeps the anvil/chat prompt byte-identically. Both defaults are the Java-only no-ops, so an engine wired
 * without Floodgate never redirects.
 */
@NullMarked
public final class TextInput {

    /**
     * The catalog key for the hint a chat prompt carries, since a chat prompt has no cancel button to click.
     * It is asked for with a {@code keyword} placeholder holding the word the caller accepts.
     */
    public static final String CANCEL_HINT_KEY = "gui.input.cancel-hint";

    /** The catalog key for the line a viewer is sent when a prompt ends without a submission. */
    public static final String CANCELLED_KEY = "gui.input.cancelled";

    private final InputSettings settings;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final AnvilTextBackend anvilBackend;
    private final ChatTextBackend chatBackend;
    private final BedrockDetector bedrock;
    private final BedrockScreen bedrockScreen;
    private final Log log;

    /**
     * The transient-sign backend a {@code sign} input point uses, or {@code null} on an engine wired without one
     * (every test fixture), in which case {@code sign} falls back to the anvil backend.
     */
    @Nullable private final TextInputBackend signBackend;

    /**
     * The native-dialog backend a {@code dialog} input point uses, or {@code null} when no dialog backend is wired: the
     * server predates the 1.21.6 Dialog API, or the seam predates the dialog backend. When {@code null}, a {@code
     * dialog} input point falls back to the sign backend (or the anvil if no sign backend either), and the seam logs
     * the substitution once through {@link #dialogFallback()} so the operator is not silently handed a sign.
     */
    @Nullable private final TextInputBackend dialogBackend;

    /** Guards the one-time {@code input_mode_unavailable} log so a repeated dialog fallback does not spam the console. */
    private final AtomicBoolean dialogFallbackWarned = new AtomicBoolean();

    /**
     * As the eight-argument constructor, but with no Bedrock redirect: every player gets the anvil or chat prompt.
     * Kept so the tests and any wiring that predates the Bedrock seam stay a delegating call.
     */
    public TextInput(
            InputSettings settings,
            GuiText guiText,
            Scheduler scheduler,
            AnvilTextBackend anvilBackend,
            ChatTextBackend chatBackend,
            Log log) {
        this(settings, guiText, scheduler, anvilBackend, chatBackend, BedrockDetector.NONE, BedrockScreen.NONE, log);
    }

    /**
     * As the ten-argument constructor, but with neither a sign nor a dialog backend: a {@code sign} or {@code dialog}
     * input point falls back to the anvil backend. Kept so the tests and any wiring that predates the native-screen
     * backends stay a delegating call.
     */
    public TextInput(
            InputSettings settings,
            GuiText guiText,
            Scheduler scheduler,
            AnvilTextBackend anvilBackend,
            ChatTextBackend chatBackend,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen,
            Log log) {
        this(settings, guiText, scheduler, anvilBackend, chatBackend, bedrock, bedrockScreen, null, null, log);
    }

    public TextInput(
            InputSettings settings,
            GuiText guiText,
            Scheduler scheduler,
            AnvilTextBackend anvilBackend,
            ChatTextBackend chatBackend,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen,
            @Nullable TextInputBackend signBackend,
            @Nullable TextInputBackend dialogBackend,
            Log log) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.anvilBackend = Objects.requireNonNull(anvilBackend, "anvilBackend");
        this.chatBackend = Objects.requireNonNull(chatBackend, "chatBackend");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.bedrockScreen = Objects.requireNonNull(bedrockScreen, "bedrockScreen");
        this.log = Objects.requireNonNull(log, "log");
        this.signBackend = signBackend;
        this.dialogBackend = dialogBackend;
    }

    /**
     * Prompt {@code player} for a line of text per the request, then run exactly one of the callbacks on the player's
     * region thread: {@code onSubmit} with the typed line, or {@code onCancel} if they cancelled (closed the anvil or
     * typed a cancel keyword).
     *
     * @param player the live player to prompt
     * @param request the input point: its key (config lookup), label, and optional pre-fill
     * @param onSubmit receives the accepted line
     * @param onCancel runs on cancellation; typically reopens the menu the player came from
     */
    public void prompt(Player player, InputRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        if (bedrock.isBedrock(player.getUniqueId())) {
            sendInputForm(player, request, onSubmit, onCancel);
            return;
        }
        InputMode mode = settings.modeFor(request.key());
        TextInputBackend backend = backendFor(mode);
        Component prompt = buildPrompt(player, request.label(), request.placeholders(), mode);
        backend.open(
                player,
                prompt,
                request.initialText(),
                result -> scheduler.entity(player, () -> route(player, result, onSubmit, onCancel)));
    }

    /**
     * As {@link #prompt}, but with the prompt already resolved to a {@link Component} rather than looked up from a
     * {@link String} catalog: the entry point the menu engine uses, whose {@code input:} prompts are arbitrary {@code
     * @key}-or-MiniMessage strings the engine resolves through its own renderer, not catalog enum keys. The backend is
     * still chosen from the operator's per-{@code key} mode, and a Bedrock player still gets the Cumulus form
     * regardless of that mode; the cancel-keyword policy and the entity-thread hop are the shared {@link #route}.
     *
     * @param player the live player to prompt
     * @param key the input-point key the per-key mode is looked up by
     * @param prompt the already-resolved prompt label
     * @param initialText the anvil pre-fill, or {@code null}
     * @param onSubmit receives the accepted line
     * @param onCancel runs on cancellation
     */
    public void promptResolved(
            Player player,
            String key,
            Component prompt,
            @Nullable String initialText,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        if (bedrock.isBedrock(player.getUniqueId())) {
            sendResolvedInputForm(player, prompt, initialText, onSubmit, onCancel);
            return;
        }
        InputMode mode = settings.modeFor(key);
        TextInputBackend backend = backendFor(mode);
        // A chat prompt has no cancel button, so it carries the abort hint; a screen backend (anvil/sign) shows the
        // prompt as its own title and needs none: the same rule buildPrompt applies to a catalog-resolved prompt.
        Component effective = mode == InputMode.CHAT ? appendCancelHint(player, prompt) : prompt;
        backend.open(
                player,
                effective,
                initialText,
                result -> scheduler.entity(player, () -> route(player, result, onSubmit, onCancel)));
    }

    /**
     * The backend for {@code mode}: chat for {@code CHAT}, the transient sign for {@code SIGN}, the native dialog for
     * {@code DIALOG}, and the anvil for {@code ANVIL}. A {@code SIGN}/{@code DIALOG} point whose backend is not wired
     * falls back to the anvil; a {@code DIALOG} fallback is logged once by {@link #dialogFallback()} rather than
     * silently masquerading as a sign or an anvil.
     */
    private TextInputBackend backendFor(InputMode mode) {
        return switch (mode) {
            case CHAT -> chatBackend;
            case DIALOG -> dialogBackend != null ? dialogBackend : dialogFallback();
            case SIGN -> signBackend != null ? signBackend : anvilBackend;
            case ANVIL -> anvilBackend;
        };
    }

    /**
     * The backend a {@code dialog} input point falls back to when no dialog backend is wired: the server predates the
     * 1.21.6 Dialog API, or the seam was built without one. The substitution is logged once (not per prompt, guarded by
     * {@link #dialogFallbackWarned}) so an operator who configured {@code dialog} and saw a sign or an anvil can find
     * out why, instead of a silent masquerade.
     */
    private TextInputBackend dialogFallback() {
        TextInputBackend sign = signBackend;
        if (dialogFallbackWarned.compareAndSet(false, true)) {
            log.warn("event=input_mode_unavailable mode=dialog fallback={}", sign != null ? "sign" : "anvil");
        }
        return sign != null ? sign : anvilBackend;
    }

    /**
     * Render the request as a Cumulus CustomForm for a Bedrock player. The label resolves to plain text through the
     * same unprefixed {@link #buildPrompt} shape the anvil uses (no chat brand prefix in a form title), and serves as
     * both the form title and the single input's label. The submit and the close both re-enter {@link #route} on the
     * player's entity thread, so the cancel-keyword policy and the Folia hop are shared with the anvil/chat backends.
     */
    private void sendInputForm(Player player, InputRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        Component label = buildPrompt(player, request.label(), request.placeholders(), InputMode.ANVIL);
        String plain = PlainTextComponentSerializer.plainText().serialize(label);
        bedrockScreen.sendInputForm(
                player,
                plain,
                plain,
                request.initialText(),
                value -> scheduler.entity(
                        player, () -> route(player, new InputResult.Submitted(value), onSubmit, onCancel)),
                () -> scheduler.entity(
                        player, () -> route(player, InputResult.Cancelled.INSTANCE, onSubmit, onCancel)));
    }

    /**
     * Render the resolved prompt as a Cumulus CustomForm for a Bedrock player: the {@link #promptResolved} counterpart
     * to {@link #sendInputForm}. The prompt is flattened to plain text for the form title and its single input's label,
     * and both the submit and the close re-enter {@link #route} on the player's entity thread, so the cancel-keyword
     * policy and the Folia hop stay shared with every other backend.
     */
    private void sendResolvedInputForm(
            Player player,
            Component prompt,
            @Nullable String initialText,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        String plain = PlainTextComponentSerializer.plainText().serialize(prompt);
        bedrockScreen.sendInputForm(
                player,
                plain,
                plain,
                initialText,
                value -> scheduler.entity(
                        player, () -> route(player, new InputResult.Submitted(value), onSubmit, onCancel)),
                () -> scheduler.entity(
                        player, () -> route(player, InputResult.Cancelled.INSTANCE, onSubmit, onCancel)));
    }

    private Component buildPrompt(Player player, String label, Map<String, String> placeholders, InputMode mode) {
        if (mode != InputMode.CHAT) {
            // An anvil shows the prompt as its title; the brand chat prefix belongs to chat lines, not an inventory
            // title, so render the label without it. A chat prompt keeps the prefix the catalog key carries.
            return guiText.textUnprefixed(player, label, placeholders);
        }
        return appendCancelHint(player, guiText.text(player, label, placeholders));
    }

    /** Append the "type &lt;keyword&gt; to cancel" hint to a chat prompt, which has no cancel button to click. */
    private Component appendCancelHint(Player player, Component prompt) {
        Component hint = guiText.text(player, CANCEL_HINT_KEY, Map.of("keyword", settings.primaryCancelKeyword()));
        return prompt.append(Component.space()).append(hint);
    }

    private void route(Player player, InputResult result, Consumer<String> onSubmit, Runnable onCancel) {
        if (result instanceof InputResult.Submitted submitted && !settings.isCancel(submitted.text())) {
            onSubmit.accept(submitted.text());
            return;
        }
        player.sendMessage(guiText.text(player, CANCELLED_KEY));
        onCancel.run();
    }
}

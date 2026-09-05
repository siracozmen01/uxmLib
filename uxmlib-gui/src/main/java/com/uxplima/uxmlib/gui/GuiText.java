package com.uxplima.uxmlib.gui;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Where a menu gets its words. The consumer answers; this library never does.
 *
 * <p>Two questions live here because a menu asks two different ones, and merging them would push raw text
 * through a catalog it was never in. {@link #text} looks a key up, which is what a screen does when a file
 * names {@code @menu.category.title} instead of holding the words. {@link #render} takes text an operator
 * already wrote in a {@code .conf} and turns it into a component, which is what a screen does for every name
 * and every lore line of every item. Different input, different frequency, and only the first has a key.
 *
 * <p><strong>No implementation ships with this library, and that is deliberate.</strong> A default that
 * resolved a key would decide what a key means; one that read {@code viewer.locale()} would decide what
 * language a player reads in, and would outrank whatever answer the consumer's own language command had
 * already given. The {@code viewer} passed here carries identity and never language: what to do with it is
 * the consumer's, including which locale it implies.
 *
 * <p>A {@code Player} rather than a {@code UUID} because every caller already holds one, and because an
 * implementation usually wants the name as well as the id and should not have to look a player up to get
 * back something it was handed.
 *
 * <p>Implementations are called on the viewer's entity thread, once per item per render, so they should read
 * from memory and not from disk or a database. They also validate their own arguments: the two abstract
 * methods cannot do it here, so the obligation is theirs and is stated rather than assumed.
 */
public interface GuiText {

    /**
     * The words {@code key} stands for, drawn for {@code viewer}, with {@code placeholders} substituted into
     * them. Never null: a key an implementation does not know still has to render as something, because the
     * alternative is a hole in a menu a player is looking at.
     */
    Component text(Player viewer, String key, Map<String, String> placeholders);

    /**
     * Text an operator wrote, turned into a component. No key and no catalog: the string arrives as it was
     * written in the file.
     *
     * <p>There is no viewer here on purpose. A line like {@code Hello %player%} is an ordinary thing to write
     * in a menu file, and it is substituted <strong>before</strong> this is called, by whoever built the
     * string. This runs once for every name and every lore line of every item, so a viewer parameter would
     * invite a per-player lookup on the hottest path the engine has, and the caller already knows the viewer
     * it is rendering for. Per-viewer substitution belongs to the caller, and the omission is a decision
     * rather than an oversight.
     */
    Component render(String raw);

    /**
     * The same words as {@link #text}, with whatever decoration a chat line carries and an inventory title
     * must not.
     *
     * <p>A catalog usually writes its prompts for chat, so a key can arrive with a brand prefix in front of
     * it. That reads correctly in a message and wrongly as the title of an anvil, where the prompt has to be
     * the only thing on the line. Only the consumer knows what its own decoration looks like, so only the
     * consumer can take it off.
     *
     * <p>This one does have a default, and it is the honest one: a catalog with no such decoration answers
     * the same as {@link #text}, which is exactly right for it. A consumer whose keys do carry a prefix and
     * does not override this gets a prefixed anvil title, which is visible on the first prompt rather than
     * silent.
     */
    default Component textUnprefixed(Player viewer, String key, Map<String, String> placeholders) {
        return text(viewer, key, placeholders);
    }

    /** The same as {@link #text}, for the many keys that carry no placeholders. */
    default Component text(Player viewer, String key) {
        return text(viewer, key, Map.of());
    }

    /**
     * {@link #text} flattened to a plain string, for the places a component cannot go: a Bedrock form label
     * is a flat string, and so is an inventory title on some paths. Formatting is dropped, not stripped from
     * the source, so the same key reads the same in both places.
     *
     * <p>Never blank where the words were not, and that is the whole reason this method is not one line.
     * Flattening a translatable component that no translator holds produces the empty string rather than the
     * key, so a label built from one does not degrade, it disappears: the player gets a blank button, and the
     * same text is correct in a lore line, which keeps the component and lets the client translate it. When
     * that is what happened, this returns {@code key} instead.
     *
     * <p>Only when that is what happened. Text an implementation meant to be empty comes back empty, because a
     * blank label somebody asked for is not a failure. The two are told apart by looking for the translatable
     * the loss requires, which is a question about the component in hand and needs no memory of earlier ones.
     *
     * <p>Returning the key is what the client already does with a translation it does not have, and it is the
     * better half of the fix: the failure lands on the screen, in words, attached to the thing that is wrong.
     * A log line would have said it once and then needed somewhere to remember that it had, and a library
     * holding that memory decides for every consumer sharing its classloader.
     *
     * <p>Total loss is what this catches, and only total loss. A component of {@code text("Yes: ")} followed by
     * a translatable flattens to {@code "Yes: "}, which is not empty, so nothing is substituted and the
     * translatable half is gone without a trace. That is left alone deliberately: splicing a key into text that
     * survived would read worse than either half. So do not read {@code hasTranslatable} below as translatables
     * being handled. The only way to avoid both losses is not to return one from {@link #text}.
     *
     * <p>An implementation that cannot rely on a registered translator should still resolve its own text
     * before returning it, and {@code GuiTextTest} pins both halves.
     */
    default String plain(Player viewer, String key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        Component words = text(viewer, key, placeholders);
        String flattened = PlainTextComponentSerializer.plainText().serialize(words);
        return flattened.isEmpty() && hasTranslatable(words) ? key : flattened;
    }

    /** Whether {@code component} carries a translatable anywhere in it, which is what flattening can lose whole. */
    private static boolean hasTranslatable(Component component) {
        if (component instanceof TranslatableComponent) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasTranslatable(child)) {
                return true;
            }
        }
        return false;
    }
}

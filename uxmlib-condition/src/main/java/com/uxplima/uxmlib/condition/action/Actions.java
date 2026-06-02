package com.uxplima.uxmlib.condition.action;

import java.util.Objects;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.uxplima.uxmlib.text.Text;

/**
 * Factory for the built-in {@link Action} closures. Each method captures the static payload of a config action
 * (a MiniMessage template, a command line, a parsed sound spec) and returns a closure that, at run time,
 * resolves placeholders against the {@link ActionContext} and performs the native delivery — Adventure {@code
 * Audience} for text and sound, the context's {@link CommandSink}s for commands, the subject player for a
 * close. Splitting closure construction out of {@link ActionParser} keeps both types small.
 *
 * <p>Text actions are flagged {@code async()} true: rendering MiniMessage and calling {@code sendMessage}/
 * {@code sendActionBar} only touches an {@code Audience} and is thread-agnostic. Command and close actions are
 * sync (the default) because dispatching a command and closing an inventory must run on the main thread.
 */
public final class Actions {

    private Actions() {}

    /** {@code [message] <template>} — render the template and send it to the target audience. */
    public static Action message(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.target().sendMessage(render(context, template)));
    }

    /** {@code [broadcast] <template>} — render the template and send it to the broadcast audience. */
    public static Action broadcast(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.broadcast().sendMessage(render(context, template)));
    }

    /** {@code [actionbar] <template>} — render the template into the target's action bar. */
    public static Action actionBar(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(context -> context.target().sendActionBar(render(context, template)));
    }

    /** {@code [title] <template>} — show the template as a title to the target (empty subtitle). */
    public static Action title(String template) {
        Objects.requireNonNull(template, "template");
        return asyncText(
                context -> context.target().showTitle(Title.title(render(context, template), Component.empty())));
    }

    /** {@code [console] <command>} — dispatch the resolved command through the console sink. */
    public static Action console(String commandTemplate) {
        Objects.requireNonNull(commandTemplate, "commandTemplate");
        return context -> context.consoleSink().dispatch(stripSlash(context.resolve(commandTemplate)));
    }

    /** {@code [player] <command>} — dispatch the resolved command through the player sink. */
    public static Action playerCommand(String commandTemplate) {
        Objects.requireNonNull(commandTemplate, "commandTemplate");
        return context -> context.playerSink().dispatch(stripSlash(context.resolve(commandTemplate)));
    }

    /** {@code [close]} — close the subject player's inventory, or do nothing when there is no player. */
    public static Action close() {
        return context -> context.player().ifPresent(player -> player.closeInventory());
    }

    /**
     * {@code [sound] <key> [volume] [pitch]} — play the parsed sound to the target. The key is resolved at run
     * time from a placeholder template, so it can be malformed (an uppercase letter, an empty or garbage
     * resolution). {@link Action} must not throw on delivery, so an unparseable key is skipped rather than
     * letting {@link Key#key(String)} raise {@link net.kyori.adventure.key.InvalidKeyException} and abort the
     * remaining actions in the list.
     */
    public static Action sound(SoundSpec spec) {
        Objects.requireNonNull(spec, "spec");
        return asyncText(context -> {
            String resolved = context.resolve(spec.keyTemplate());
            if (!Key.parseable(resolved)) {
                return;
            }
            context.target()
                    .playSound(Sound.sound(Key.key(resolved), Sound.Source.MASTER, spec.volume(), spec.pitch()));
        });
    }

    private static Component render(ActionContext context, String template) {
        return Text.mini(context.resolve(template));
    }

    private static String stripSlash(String commandLine) {
        String stripped = commandLine.strip();
        return stripped.startsWith("/") ? stripped.substring(1) : stripped;
    }

    private static Action asyncText(Action delegate) {
        return new Action() {
            @Override
            public void run(ActionContext context) {
                delegate.run(context);
            }

            @Override
            public boolean async() {
                return true;
            }
        };
    }

    /** The static structure of a {@code [sound]} payload: the key template plus a volume and pitch. */
    public record SoundSpec(String keyTemplate, float volume, float pitch) {

        /** Canonical constructor null-checks the key template; volume and pitch are plain floats. */
        public SoundSpec {
            Objects.requireNonNull(keyTemplate, "keyTemplate");
        }
    }
}

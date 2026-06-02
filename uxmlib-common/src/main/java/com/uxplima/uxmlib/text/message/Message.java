package com.uxplima.uxmlib.text.message;

import java.time.Duration;
import java.util.Objects;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/**
 * An admin-retargetable message: the same catalog value carries both its MiniMessage template and the
 * delivery channel, so swapping chat for a title or an action bar is a config edit with zero code change.
 * Modelled as a sealed type whose variants — {@link Chat}, {@link TitleText}, {@link ActionBar},
 * {@link BossBarText}, {@link Silent} — each hold their own delivery parameters. A {@link MessageSerializer}
 * reads and writes them from HOCON.
 *
 * <p>{@link #template()} is the raw MiniMessage string; the facade parses it (resolving placeholders) and
 * passes the rendered {@link Component} to {@link #send(Audience, Component)}, which dispatches to the native
 * Adventure delivery method for its channel. No packets, no scheduler — these are one-shot sends.
 */
public sealed interface Message
        permits Message.Chat, Message.TitleText, Message.ActionBar, Message.BossBarText, Message.Silent {

    /** The raw MiniMessage template carried by this message (empty for {@link Silent}). */
    String template();

    /** Deliver the already-rendered {@code content} to {@code viewer} over this message's channel. */
    void send(Audience viewer, Component content);

    /** A literal-string message wrapped as plain chat, the default channel. */
    static Message chat(String template) {
        return new Chat(template);
    }

    /** Chat delivery via {@link Audience#sendMessage(Component)}. */
    record Chat(String template) implements Message {
        public Chat {
            Objects.requireNonNull(template, "template");
        }

        @Override
        public void send(Audience viewer, Component content) {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(content, "content");
            viewer.sendMessage(content);
        }
    }

    /**
     * Title delivery via {@link Audience#showTitle(Title)}. The rendered content is the title line; the
     * {@code subtitle} template (which may be blank) and the fade/stay/fade-out durations are intrinsic to
     * the variant and rendered by the facade.
     */
    record TitleText(String template, String subtitle, Duration fadeIn, Duration stay, Duration fadeOut)
            implements Message {
        public TitleText {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(subtitle, "subtitle");
            requireNonNegative(fadeIn, "fadeIn");
            requireNonNegative(stay, "stay");
            requireNonNegative(fadeOut, "fadeOut");
        }

        @Override
        public void send(Audience viewer, Component content) {
            send(viewer, content, Component.empty());
        }

        /** Show with an explicit, already-rendered subtitle component. */
        public void send(Audience viewer, Component title, Component renderedSubtitle) {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(renderedSubtitle, "renderedSubtitle");
            Title.Times times = Title.Times.times(fadeIn, stay, fadeOut);
            viewer.showTitle(Title.title(title, renderedSubtitle, times));
        }
    }

    /** Action-bar delivery via {@link Audience#sendActionBar(Component)}. */
    record ActionBar(String template) implements Message {
        public ActionBar {
            Objects.requireNonNull(template, "template");
        }

        @Override
        public void send(Audience viewer, Component content) {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(content, "content");
            viewer.sendActionBar(content);
        }
    }

    /**
     * Boss-bar delivery via {@link Audience#showBossBar(BossBar)}. The content is the bar title; the
     * progress, colour and overlay are intrinsic. A boss bar persists on the client until it is hidden or the
     * viewer disconnects — there is no auto-expiry — so a caller that needs to remove it must keep the handle
     * {@link #show(Audience, Component)} returns and later {@link Audience#hideBossBar(BossBar) hide} it; a
     * timed bar is the HUD layer's job. The {@link #send(Audience, Component)} fire-and-forget form discards
     * that handle, so use it only when something else already owns the bar's lifecycle.
     */
    record BossBarText(String template, float progress, BossBar.Color color, BossBar.Overlay overlay)
            implements Message {
        public BossBarText {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(overlay, "overlay");
            if (progress < 0f || progress > 1f) {
                throw new IllegalArgumentException("progress must be within [0, 1]: " + progress);
            }
        }

        @Override
        public void send(Audience viewer, Component content) {
            show(viewer, content);
        }

        /**
         * Show the bar and return its {@link BossBar} handle so the caller can later
         * {@link Audience#hideBossBar(BossBar) hide} it; without retaining this the bar can never be removed.
         */
        public BossBar show(Audience viewer, Component content) {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(content, "content");
            BossBar bar = BossBar.bossBar(content, progress, color, overlay);
            viewer.showBossBar(bar);
            return bar;
        }
    }

    /** A message that is deliberately not shown — the way config silences one channel without removing it. */
    record Silent() implements Message {
        @Override
        public String template() {
            return "";
        }

        @Override
        public void send(Audience viewer, Component content) {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(content, "content");
            // Intentionally no-op: silencing a message is a first-class channel, not an error.
        }
    }

    private static void requireNonNegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}

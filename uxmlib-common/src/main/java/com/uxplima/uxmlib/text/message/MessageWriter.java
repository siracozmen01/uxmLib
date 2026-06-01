package com.uxplima.uxmlib.text.message;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * The write half of {@link MessageSerializer}, split out to keep both classes within the size cap. Each
 * variant is emitted as a typed HOCON section so a saved config round-trips back through the reader; a plain
 * {@link Message.Chat} could collapse to a bare string, but the explicit {@code type = chat} form keeps every
 * saved entry self-describing and editable.
 */
final class MessageWriter {

    private MessageWriter() {}

    static void write(Message message, ConfigurationNode node) throws SerializationException {
        switch (message) {
            case Message.Chat chat -> {
                node.node("type").set("chat");
                node.node("text").set(chat.template());
            }
            case Message.ActionBar actionBar -> {
                node.node("type").set("actionbar");
                node.node("text").set(actionBar.template());
            }
            case Message.TitleText title -> writeTitle(title, node);
            case Message.BossBarText bar -> writeBossBar(bar, node);
            case Message.Silent ignored -> node.node("type").set("silent");
        }
    }

    private static void writeTitle(Message.TitleText title, ConfigurationNode node) throws SerializationException {
        node.node("type").set("title");
        node.node("text").set(title.template());
        node.node("subtitle").set(title.subtitle());
        node.node("fadeIn").set(title.fadeIn().toMillis());
        node.node("stay").set(title.stay().toMillis());
        node.node("fadeOut").set(title.fadeOut().toMillis());
    }

    private static void writeBossBar(Message.BossBarText bar, ConfigurationNode node) throws SerializationException {
        node.node("type").set("bossbar");
        node.node("text").set(bar.template());
        node.node("progress").set(bar.progress());
        node.node("color").set(net.kyori.adventure.bossbar.BossBar.Color.NAMES.key(bar.color()));
        node.node("overlay").set(net.kyori.adventure.bossbar.BossBar.Overlay.NAMES.key(bar.overlay()));
    }
}

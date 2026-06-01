package com.uxplima.uxmlib.text.message;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import net.kyori.adventure.bossbar.BossBar;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

/**
 * Configurate serializer for {@link Message}, so an admin defines a message's delivery channel in HOCON.
 * A bare string is read as plain {@link Message.Chat}; a section {@code {type = title, text = "...", ...}}
 * selects a variant and reads its parameters. This is what makes a message admin-retargetable: changing
 * {@code type = chat} to {@code type = title} re-routes the same text with no code change.
 *
 * <p>Title durations are stored as integer milliseconds (a translator-friendly unit); boss-bar colour and
 * overlay are stored by their Adventure enum names. Unknown values fail loudly with a clear message rather
 * than silently degrading.
 */
public final class MessageSerializer implements TypeSerializer<Message> {

    private static final Duration DEFAULT_FADE = Duration.ofMillis(500L);
    private static final Duration DEFAULT_STAY = Duration.ofSeconds(3L);

    /** A serializer collection with {@link Message} registered, for {@code HoconConfig.load(path, ...)}. */
    public static TypeSerializerCollection collection() {
        return TypeSerializerCollection.defaults()
                .childBuilder()
                .register(Message.class, new MessageSerializer())
                .build();
    }

    @Override
    public Message deserialize(Type type, ConfigurationNode node) throws SerializationException {
        Objects.requireNonNull(node, "node");
        if (!node.isMap()) {
            return new Message.Chat(requireString(node, "text"));
        }
        String channel = node.node("type").getString("chat").trim().toLowerCase(Locale.ROOT);
        return switch (channel) {
            case "chat" -> new Message.Chat(text(node));
            case "title" -> title(node);
            case "actionbar", "action_bar" -> new Message.ActionBar(text(node));
            case "bossbar", "boss_bar" -> bossBar(node);
            case "silent", "none" -> new Message.Silent();
            default -> throw new SerializationException("unknown message type: " + channel);
        };
    }

    private static Message title(ConfigurationNode node) throws SerializationException {
        return new Message.TitleText(
                text(node),
                node.node("subtitle").getString(""),
                millis(node, "fadeIn", DEFAULT_FADE),
                millis(node, "stay", DEFAULT_STAY),
                millis(node, "fadeOut", DEFAULT_FADE));
    }

    private static Message bossBar(ConfigurationNode node) throws SerializationException {
        return new Message.BossBarText(
                text(node),
                (float) node.node("progress").getDouble(1.0d),
                colour(node.node("color").getString("WHITE")),
                overlay(node.node("overlay").getString("PROGRESS")));
    }

    private static String text(ConfigurationNode node) throws SerializationException {
        return requireString(node, "text");
    }

    private static String requireString(ConfigurationNode node, String field) throws SerializationException {
        String value = node.isMap() ? node.node(field).getString() : node.getString();
        if (value == null) {
            throw new SerializationException("message is missing its '" + field + "'");
        }
        return value;
    }

    private static Duration millis(ConfigurationNode node, String field, Duration fallback) {
        ConfigurationNode child = node.node(field);
        return child.virtual() || child.isNull() ? fallback : Duration.ofMillis(child.getLong());
    }

    private static BossBar.Color colour(@Nullable String raw) throws SerializationException {
        BossBar.Color value = BossBar.Color.NAMES.value(name(raw, "color"));
        if (value == null) {
            throw new SerializationException("unknown boss-bar colour: " + raw);
        }
        return value;
    }

    private static BossBar.Overlay overlay(@Nullable String raw) throws SerializationException {
        BossBar.Overlay value = BossBar.Overlay.NAMES.value(name(raw, "overlay"));
        if (value == null) {
            throw new SerializationException("unknown boss-bar overlay: " + raw);
        }
        return value;
    }

    private static String name(@Nullable String raw, String field) throws SerializationException {
        if (raw == null) {
            throw new SerializationException("missing boss-bar " + field);
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void serialize(Type type, @Nullable Message message, ConfigurationNode node) throws SerializationException {
        if (message == null) {
            node.raw(null);
            return;
        }
        MessageWriter.write(message, node);
    }
}

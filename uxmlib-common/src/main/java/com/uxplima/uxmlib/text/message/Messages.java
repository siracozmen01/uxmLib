package com.uxplima.uxmlib.text.message;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmlib.text.Text;

/**
 * The i18n facade: resolves a viewer's locale, looks up the locale-specific template for a {@link MessageKey}
 * (three-tier fallback via {@link MessageCatalog}), renders it through MiniMessage with the supplied
 * placeholders, and delivers it over the channel configured for that key.
 *
 * <p>The channel comes from an admin-supplied {@code channels} map (a key mapped to a {@link Message} whose
 * variant selects chat/title/action-bar/boss-bar/silent); a key with no entry defaults to plain chat. The
 * template text always comes from the catalog, so a translator edits text and an operator edits the channel
 * independently. Constructor-injected, no static state.
 */
public final class Messages {

    private final MessageCatalog catalog;
    private final LocaleSource locales;
    private final Map<String, Message> channels;

    /** A chat-only facade: every key is delivered as chat with the catalog's template. */
    public Messages(MessageCatalog catalog, LocaleSource locales) {
        this(catalog, locales, Map.of());
    }

    /**
     * @param channels per-key delivery channels, addressed by {@link MessageKey#path()}; a key absent here is
     *     delivered as chat. Copied defensively.
     */
    public Messages(MessageCatalog catalog, LocaleSource locales, Map<String, Message> channels) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.locales = Objects.requireNonNull(locales, "locales");
        this.channels = Map.copyOf(Objects.requireNonNull(channels, "channels"));
    }

    /** Render {@code key} for {@code viewer}'s locale, substituting {@code resolvers}, and deliver it. */
    public void send(Audience viewer, MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolvers, "resolvers");
        Component content = render(viewer, key, resolvers);
        Message channel = channel(key);
        // A title carries its own subtitle template (intrinsic to the channel, not the catalog); render it
        // with the same placeholders and use the subtitle-aware send so a configured subtitle reaches the
        // player. The plain two-arg send would drop it.
        if (channel instanceof Message.TitleText title) {
            title.send(viewer, content, Text.mini(title.subtitle(), resolvers));
            return;
        }
        channel.send(viewer, content);
    }

    /** The rendered {@link Component} for {@code key} in {@code viewer}'s locale, without delivering it. */
    public Component render(Audience viewer, MessageKey key, TagResolver... resolvers) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolvers, "resolvers");
        Locale locale = locales.localeOf(viewer);
        String template = catalog.template(key, locale);
        return Text.mini(template, resolvers);
    }

    private Message channel(MessageKey key) {
        Message configured = channels.get(key.path());
        return configured != null ? configured : new Message.Chat(key.defaultTemplate());
    }

    /** The catalog this facade reads from, for callers that need direct template access. */
    public MessageCatalog catalog() {
        return catalog;
    }
}

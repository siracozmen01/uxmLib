package com.uxplima.uxmlib.text.language;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.message.LocaleSource;

/**
 * Answers which language a viewer reads, in one fixed order:
 *
 * <ol>
 *   <li>the language the server forces, when it forces one;
 *   <li>the player's own choice, from a registered {@link LanguageService} first and this plugin's own
 *       {@link PlayerLanguages} second;
 *   <li>the client's language, when the server follows it: the last one remembered, else the one the client
 *       reports now;
 *   <li>the configured default.
 * </ol>
 *
 * <p>The remembered client language comes before the live one on purpose. A client reports its language after
 * it joins, so at the moment the first message is drawn the live value is still the server's own default and
 * a player would read one line in the wrong language on every join. What was remembered was reported, so it
 * is the better answer until the client speaks again.
 *
 * <p>This resolves a language and never checks whether a plugin has a file for it. That belongs to the
 * catalog, which falls back through the default locale to the key's own text, so a viewer whose language
 * nobody translated still reads a finished message.
 */
public final class LanguageResolver implements LocaleSource {

    private final Supplier<LanguageSettings> settings;

    private final PlayerLanguages store;

    private final Supplier<Optional<LanguageService>> service;

    /** A resolver with no network provider: the plugin answers alone. */
    public LanguageResolver(LanguageSettings settings, PlayerLanguages store) {
        this(settings, store, Optional::empty);
    }

    /**
     * @param service looked up on every call rather than held, because a provider is a plugin that may enable
     *     after this one and may be disabled while the server runs
     */
    public LanguageResolver(
            LanguageSettings settings, PlayerLanguages store, Supplier<Optional<LanguageService>> service) {
        this(() -> settings, store, service);
    }

    /**
     * The same, with the settings read on every call.
     *
     * <p>A plugin that swaps in a new snapshot when an operator reloads passes its own reader here, so the
     * new default, the new follow-client and the new forced language take effect without the resolver being
     * built again. Every port already holds this one instance, and rebuilding it would leave them on the old
     * file.
     */
    public LanguageResolver(
            Supplier<LanguageSettings> settings, PlayerLanguages store, Supplier<Optional<LanguageService>> service) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = Objects.requireNonNull(store, "store");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Locale defaultLocale() {
        return settings.get().defaultLocale();
    }

    @Override
    public Locale localeOf(Audience viewer) {
        Objects.requireNonNull(viewer, "viewer");
        Optional<Locale> forced = settings.get().forcedLocale();
        if (forced.isPresent()) {
            return forced.get();
        }
        if (!(viewer instanceof Player player)) {
            return settings.get().defaultLocale();
        }
        return chosenBy(player.getUniqueId())
                .or(() -> fromClient(player))
                .orElseGet(() -> settings.get().defaultLocale());
    }

    /**
     * Record a player's choice, in the provider when there is one and in this plugin's own store either way.
     *
     * <p>The local write is not a duplicate. It is what keeps the choice when a provider is removed from the
     * server, and what answers while a provider is starting up.
     */
    public void choose(UUID player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        service.get().ifPresent(provider -> provider.choose(player, locale));
        store.choose(player, locale);
    }

    /** Drop a player's choice, in the provider as well as here, so the client or the default answers again. */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        service.get().ifPresent(provider -> provider.forget(player));
        store.forget(player);
    }

    /**
     * Record what a client reports, so this player's next join opens in the right language, here and on every
     * server a provider reaches.
     */
    public void rememberClient(UUID player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        service.get().ifPresent(provider -> provider.rememberClientLanguage(player, locale));
        store.rememberClient(player, locale);
    }

    private Optional<Locale> chosenBy(UUID player) {
        return service.get().flatMap(provider -> provider.languageOf(player)).or(() -> store.chosen(player));
    }

    private Optional<Locale> fromClient(Player player) {
        if (!settings.get().followClient()) {
            return Optional.empty();
        }
        UUID who = player.getUniqueId();
        return remembered(who).or(() -> Optional.of(player.locale()));
    }

    private Optional<Locale> remembered(UUID player) {
        return service.get()
                .flatMap(provider -> provider.lastClientLanguageOf(player))
                .or(() -> store.lastClient(player));
    }
}

package com.uxplima.uxmlib.condition.wallet;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

/**
 * The one door onto PlaceholderAPI for a wallet.
 *
 * <p>Nothing outside the inner class names a type of that plugin, and the inner class is reached only
 * after the plugin manager has said the plugin is here. A server without it therefore loads none of its
 * code, and every read answers with nothing.
 *
 * <p>PlaceholderAPI expansions are written for the thread the server ticks on, so the driver has to ask on
 * it. The {@code [take-money]} action declares itself sync for exactly that reason.
 */
public final class ServerPlaceholders implements PlaceholderWallet.Reader {

    /** The plugin name, which is the present-guard. */
    public static final String PLUGIN = "PlaceholderAPI";

    @Override
    public Optional<String> read(Player player, String placeholder) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(placeholder, "placeholder");
        if (!ServerEconomyProviders.isPresent(PLUGIN)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(Answered.of(player, placeholder));
        } catch (RuntimeException | LinkageError unreachable) {
            return Optional.empty();
        }
    }

    /** The call itself, in a class of its own, so the type of the other plugin is read only here. */
    private static final class Answered {

        private Answered() {}

        private static String of(Player player, String placeholder) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder);
        }
    }
}

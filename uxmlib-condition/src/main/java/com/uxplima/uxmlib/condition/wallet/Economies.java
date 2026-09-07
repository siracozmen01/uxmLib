package com.uxplima.uxmlib.condition.wallet;

import java.util.Objects;

import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Access;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Answer;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Argument;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Calls;
import com.uxplima.uxmlib.condition.wallet.EconomyBinding.Pools;

/**
 * The economy plugins {@link BridgedWallet} knows how to talk to.
 *
 * <p>Each one is a description and not a class, so a consumer that needs a fifth writes an {@link
 * EconomyBinding} of its own and needs nothing from this library. The version each description was read
 * against is in its comment: an economy that renames a method in a later version turns its own bridge off,
 * and the log says so once.
 */
public final class Economies {

    private Economies() {}

    /** Vault, the one nearly every server already has. One balance, and a response object per call. */
    public static EconomyBinding vault() {
        return new EconomyBinding(
                "Vault",
                "net.milkbowl.vault.economy.Economy",
                Access.SERVICE,
                null,
                "getBalance",
                "withdrawPlayer",
                Argument.OFFLINE_PLAYER,
                Answer.VAULT_RESPONSE,
                Pools.one(),
                Calls.simple());
    }

    /**
     * VaultUnlocked, read against the published API of version 2.20.2.
     *
     * <p>It is the modern Vault and the first choice where it is installed: the plugin is still called
     * Vault, and it registers {@code net.milkbowl.vault2.economy.Economy} beside the old one. Two things
     * differ. The account is a {@code UUID} rather than an offline player, and every call asks who is
     * moving the money, so the caller has to introduce itself.
     *
     * <p>A named currency of a VaultUnlocked economy is not described here, because those methods want a
     * world name as well and a world is not a thing a wallet has an opinion about.
     *
     * @param caller what the calling plugin calls itself, which VaultUnlocked records against the move
     */
    public static EconomyBinding vaultUnlocked(String caller) {
        Objects.requireNonNull(caller, "caller");
        if (caller.isBlank()) {
            throw new IllegalArgumentException("VaultUnlocked asks who is moving the money, so name the caller");
        }
        return new EconomyBinding(
                "Vault",
                "net.milkbowl.vault2.economy.Economy",
                Access.SERVICE,
                null,
                "getBalance",
                "withdraw",
                Argument.PLAYER_ID,
                Answer.VAULT_RESPONSE,
                Pools.one(),
                new Calls(false, caller));
    }

    /** PlayerPoints, whose API sits behind two static hops. Whole numbers, and a plain boolean back. */
    public static EconomyBinding playerPoints() {
        return new EconomyBinding(
                "PlayerPoints",
                "org.black_ixx.playerpoints.PlayerPoints",
                Access.STATIC,
                "getInstance.getAPI",
                "look",
                "take",
                Argument.PLAYER_ID,
                Answer.BOOLEAN,
                Pools.one(),
                Calls.simple());
    }

    /**
     * EcoBits, read against the published artifact of version 2026.34.
     *
     * <p>It is four shapes at once, and each of them is why the description language has them. The methods
     * are static on a utility class. The amount is a decimal. The currency is an object, fetched by the
     * name the caller asks for. And there is no take: {@code adjustBalance} is a give, and a take is a give
     * of a negative number.
     *
     * <p>Because that take cannot refuse, {@link BridgedWallet} reads the balance first and refuses the
     * whole cost before anything moves. See {@link Answer#NOTHING}.
     */
    public static EconomyBinding ecoBits() {
        return new EconomyBinding(
                "EcoBits",
                "com.willfp.ecobits.currencies.CurrencyUtils",
                Access.CLASS,
                null,
                "getBalance",
                "adjustBalance",
                Argument.OFFLINE_PLAYER,
                Answer.NOTHING,
                Pools.byObject("com.willfp.ecobits.currencies.Currencies", "getByID"),
                new Calls(true, null));
    }
}

package com.uxplima.uxmlib.condition.wallet;

import org.bukkit.Bukkit;

/**
 * The console, as a wallet reaches it.
 *
 * <p>The answer is only whether the server took the line. A command says nothing about what the plugin
 * behind it did, and {@link PlaceholderWallet} says what that costs.
 *
 * <p>A command runs on the thread the server ticks on, and this sends it on the thread it is called from,
 * because the lane is the driver's decision and not this class's. The {@code [take-money]} action declares
 * itself sync, so a driver that runs an action list the ordinary way is already on the right thread.
 */
public final class ConsoleCommands {

    private ConsoleCommands() {}

    /** The server's own console. */
    public static PlaceholderWallet.Console ofServer() {
        return line -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
    }
}

/**
 * The wallet backends this module ships, each one an implementation of {@link
 * com.uxplima.uxmlib.condition.Wallet} and nothing more.
 *
 * <p>A backend answers two questions: what a player's balance is, and whether a whole amount can be taken.
 * It fixes no price, no cost table, no currency name and no way of writing a number for a player to read.
 * Those are the game a plugin plays, and they stay in the plugin.
 *
 * <p>There are three of them, and the first covers most servers:
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.condition.wallet.BridgedWallet}: one economy plugin, described by an
 *       {@link com.uxplima.uxmlib.condition.wallet.EconomyBinding} rather than compiled against. {@link
 *       com.uxplima.uxmlib.condition.wallet.Economies} holds the descriptions of Vault, VaultUnlocked,
 *       PlayerPoints and EcoBits, and an operator's own economy is one more description.
 *   <li>{@link com.uxplima.uxmlib.condition.wallet.TreasuryWallet}: Treasury, which answers a subscriber
 *       instead of returning and so fits in no description.
 *   <li>{@link com.uxplima.uxmlib.condition.wallet.PlaceholderWallet}: a balance a placeholder answers
 *       with, taken by a console line. It is the last resort for an economy that publishes no API.
 * </ul>
 *
 * <p>Every one of them is soft. A typed reference to another plugin sits inside a method that runs only
 * once the plugin manager has been asked whether that plugin is here, the handle is resolved on first use
 * and kept, and any {@link java.lang.Throwable} out of a missing or renamed API is read as "absent". A
 * server with none of these plugins installed behaves as though this package were not here.
 *
 * <p>None of them takes a part of a cost. Where the economy itself refuses an overdraft the refusal is
 * read straight back; where it does not, the balance is read first and the take is refused before
 * anything moves.
 */
@NullMarked
package com.uxplima.uxmlib.condition.wallet;

import org.jspecify.annotations.NullMarked;
